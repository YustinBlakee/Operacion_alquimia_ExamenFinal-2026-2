package com.alquimia.domain;

import java.util.List;
import static com.alquimia.domain.AlquimiaException.Code.CONFLICTING_MESSAGE;
import static com.alquimia.domain.AlquimiaException.Code.INCOMPLETE_MESSAGE;

/** Combina fragmentos por su posicion original, sin inferir desplazamientos. */
final class MessageReconstructor {
    String reconstruct(List<Reading> readings) {
        int length = readings.stream().mapToInt(reading -> reading.message().size()).max().orElse(0);
        if (length == 0) {
            throw new AlquimiaException(INCOMPLETE_MESSAGE, "No se recibieron palabras del mensaje.");
        }
        String[] words = new String[length];
        for (int i = 0; i < length; i++) {
            for (Reading reading : readings) {
                // Un fragmento mas corto no aporta informacion para las posiciones restantes.
                if (i >= reading.message().size()) continue;
                String word = reading.message().get(i);
                if (word.isEmpty()) continue;
                if (words[i] != null && !words[i].equals(word)) {
                    throw new AlquimiaException(CONFLICTING_MESSAGE,
                        "Palabras contradictorias en la posicion " + i + ".");
                }
                words[i] = word;
            }
            if (words[i] == null) {
                throw new AlquimiaException(INCOMPLETE_MESSAGE, "Falta la palabra en la posicion " + i + ".");
            }
        }
        return String.join(" ", words);
    }
}
