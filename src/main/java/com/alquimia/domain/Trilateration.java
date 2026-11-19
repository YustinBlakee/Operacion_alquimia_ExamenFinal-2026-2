package com.alquimia.domain;

import java.util.List;
import static com.alquimia.domain.AlquimiaException.Code.INCONSISTENT_DISTANCES;

/** Calculo numerico para las tres posiciones fijas, sin dependencias de infraestructura. */
final class Trilateration {
    private static final int MAX_ITERATIONS = 20;
    private static final int MAX_BACKTRACKS = 16;
    private final double distanceTolerance;
    private final double numericalTargetSquared;

    Trilateration(double distanceTolerance) {
        if (!Double.isFinite(distanceTolerance) || distanceTolerance <= 0) {
            throw new IllegalArgumentException("La tolerancia de distancia debe ser finita y mayor que cero.");
        }
        this.distanceTolerance = distanceTolerance;
        double numericalTarget = Math.min(1e-7, distanceTolerance / 10);
        numericalTargetSquared = numericalTarget * numericalTarget;
    }

    Position locate(List<Reading> readings) {
        Position result = refine(linearEstimate(readings), readings);
        for (Reading reading : readings) {
            double residual = distance(result, reading.device().position()) - reading.distance();
            if (!Double.isFinite(residual) || Math.abs(residual) > distanceTolerance) {
                throw new AlquimiaException(INCONSISTENT_DISTANCES,
                    "No se encontro una ubicacion finita compatible con las distancias (tolerancia: " + distanceTolerance + " unidades).");
            }
        }
        return result;
    }

    private Position linearEstimate(List<Reading> readings) {
        Reading first = readings.get(0), second = readings.get(1), third = readings.get(2);
        Position p = first.device().position(), q = second.device().position(), r = third.device().position();
        double a = 2 * (q.x() - p.x()), b = 2 * (q.y() - p.y());
        double c = circleDifference(first, second);
        double d = 2 * (r.x() - p.x()), e = 2 * (r.y() - p.y());
        double f = circleDifference(first, third);
        // Las posiciones fijas del catalogo no son colineales.
        double determinant = a * e - b * d;
        return new Position(Math.fma(c, e, -b * f) / determinant,
            Math.fma(a, f, -c * d) / determinant);
    }

    private double circleDifference(Reading first, Reading other) {
        Position p = first.device().position(), q = other.device().position();
        // (d1-d2)(d1+d2) evita restar directamente cuadrados muy grandes y cercanos.
        return (first.distance() - other.distance()) * (first.distance() + other.distance())
            + q.x() * q.x() + q.y() * q.y() - p.x() * p.x() - p.y() * p.y();
    }

    private Position refine(Position estimate, List<Reading> readings) {
        Position current = estimate;
        double error = squaredError(current, readings);
        for (int iteration = 0; iteration < MAX_ITERATIONS && error > numericalTargetSquared; iteration++) {
            Position step = gaussNewtonStep(current, readings);
            if (step == null || !Double.isFinite(step.x()) || !Double.isFinite(step.y())) break;
            boolean improved = false;
            double scale = 1;
            for (int attempt = 0; attempt < MAX_BACKTRACKS; attempt++, scale *= 0.5) {
                Position candidate = new Position(current.x() + scale * step.x(), current.y() + scale * step.y());
                double candidateError = squaredError(candidate, readings);
                if (candidateError < error) {
                    current = candidate;
                    error = candidateError;
                    improved = true;
                    break;
                }
            }
            if (!improved) break;
        }
        return current;
    }

    private Position gaussNewtonStep(Position current, List<Reading> readings) {
        double[][] jacobian = new double[3][2];
        double[] residuals = new double[3];
        for (int i = 0; i < readings.size(); i++) {
            Reading reading = readings.get(i);
            Position sensor = reading.device().position();
            double range = distance(current, sensor);
            if (range > 0) {
                jacobian[i][0] = (current.x() - sensor.x()) / range;
                jacobian[i][1] = (current.y() - sensor.y()) / range;
            }
            residuals[i] = reading.distance() - range;
        }
        // QR por rotaciones de Givens; formar J^T J perderia precision con dispositivos lejanos.
        for (int column = 0; column < 2; column++) {
            for (int row = 2; row > column; row--) {
                double upper = jacobian[row - 1][column], lower = jacobian[row][column];
                double norm = Math.hypot(upper, lower);
                if (norm == 0) continue;
                double cosine = upper / norm, sine = lower / norm;
                for (int j = column; j < 2; j++) {
                    double top = jacobian[row - 1][j], bottom = jacobian[row][j];
                    jacobian[row - 1][j] = cosine * top + sine * bottom;
                    jacobian[row][j] = -sine * top + cosine * bottom;
                }
                double top = residuals[row - 1], bottom = residuals[row];
                residuals[row - 1] = cosine * top + sine * bottom;
                residuals[row] = -sine * top + cosine * bottom;
            }
        }
        if (Math.abs(jacobian[0][0]) < 1e-14 || Math.abs(jacobian[1][1]) < 1e-14) return null;
        double y = residuals[1] / jacobian[1][1];
        return new Position((residuals[0] - jacobian[0][1] * y) / jacobian[0][0], y);
    }

    private double squaredError(Position position, List<Reading> readings) {
        double error = 0;
        for (Reading reading : readings) {
            double residual = distance(position, reading.device().position()) - reading.distance();
            error = Math.fma(residual, residual, error);
        }
        return error;
    }

    private double distance(Position first, Position second) {
        return Math.hypot(first.x() - second.x(), first.y() - second.y());
    }
}
