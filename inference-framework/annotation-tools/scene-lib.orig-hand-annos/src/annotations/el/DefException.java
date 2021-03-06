package annotations.el;

import annotations.*;

/**
 * Thrown by {@link DefCollector} if the scene contains two different
 * definitions of the same annotation type that cannot be
 * {@linkplain AnnotationDef#unify unified}.
 */
public class DefException extends Exception {
    private static final long serialVersionUID = 1152640422L;

    /**
     * The name of the annotation type that had two conflicting definitions.
     */
    public final /*@NonNull*/ String annotationType;

    DefException(/*@NonNull*/ String annotationType) {
        super("Conflicting definition of annotation type " + annotationType);
        this.annotationType = annotationType;
    }
}
