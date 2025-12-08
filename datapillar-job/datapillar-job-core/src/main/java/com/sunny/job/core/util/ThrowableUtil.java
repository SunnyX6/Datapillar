package com.sunny.job.core.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public class ThrowableUtil {

    /**
     * parse error to string
     *
     * @param e
     * @return
     */
    public static String toString(Throwable e) {
        StringWriter stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));
        String errorMsg = stringWriter.toString();
        return errorMsg;
    }

}
