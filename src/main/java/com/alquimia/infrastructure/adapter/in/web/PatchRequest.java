package com.alquimia.infrastructure.adapter.in.web;

import com.alquimia.domain.AlquimiaException;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.List;
import static com.alquimia.domain.AlquimiaException.Code.INVALID_DATA;

/** Distingue un campo omitido de un null explicito. */
public final class PatchRequest {
    private Double distance;
    private List<String> message;
    private boolean distancePresent;
    private boolean messagePresent;

    @JsonSetter("distance")
    public void setDistance(Double distance) { this.distance = distance; distancePresent = true; }
    @JsonSetter("message")
    public void setMessage(List<String> message) { this.message = message; messagePresent = true; }
    public Double distance() { return distance; }
    public List<String> message() { return message; }

    public void validate() {
        if ((!distancePresent && !messagePresent) || (distancePresent && distance == null)
            || (messagePresent && message == null)) {
            throw new AlquimiaException(INVALID_DATA, "Incluya distance o message con valores no nulos.");
        }
    }
}
