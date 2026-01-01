package nan.produced.prism.core.assistant.application.ingest;

public final class VectorLiterals {

    private VectorLiterals() {
    }

    public static String toPgVectorLiteral(float[] vector) {
        if (vector == null) {
            throw new IllegalArgumentException("vector is null");
        }
        StringBuilder sb = new StringBuilder(vector.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}

