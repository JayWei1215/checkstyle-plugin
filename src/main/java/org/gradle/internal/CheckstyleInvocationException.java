package org.gradle.internal;

import org.gradle.api.GradleException;
import org.gradle.internal.exceptions.Contextual;

@Contextual
class CheckstyleInvocationException extends GradleException {
    CheckstyleInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
