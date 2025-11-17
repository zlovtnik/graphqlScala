package com.rcs.ssf.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * HttpServletRequest wrapper that caches the request body for multiple reads.
 * 
 * Enforces a maximum body size to prevent OOM attacks. Async I/O listeners are not supported.
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private static final long DEFAULT_MAX_BODY_SIZE = 1_000_000L; // 1MB default

    private final String body;
    private final Charset bodyCharset;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        this(request, DEFAULT_MAX_BODY_SIZE);
    }

    /**
     * Creates a new cached request with a configurable maximum body size.
     *
     * @param request the original HttpServletRequest
     * @param maxBodySizeBytes maximum allowed request body size in bytes
     * @throws IOException if the request body cannot be read
     */
    public CachedBodyHttpServletRequest(HttpServletRequest request, long maxBodySizeBytes) throws IOException {
        super(request);
        String encoding = request.getCharacterEncoding();
        this.bodyCharset = (encoding == null || encoding.isBlank())
                ? StandardCharsets.UTF_8
                : Charset.forName(encoding);
        
        StringBuilder stringBuilder = new StringBuilder();
        long estimatedBytes = 0L;
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(request.getInputStream(), bodyCharset))) {
            char[] charBuffer = new char[128];
            int bytesRead;
            while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                // Check size limit (conservative UTF-8 upper bound: 4 bytes per char)
                estimatedBytes += (long) bytesRead * 4;
                if (estimatedBytes > maxBodySizeBytes) {
                    throw new IOException("Request body exceeds maximum allowed size of " + maxBodySizeBytes + " bytes");
                }
                stringBuilder.append(charBuffer, 0, bytesRead);
            }
        }
        body = stringBuilder.toString();
    }

    public String getBody() {
        return body;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body.getBytes(bodyCharset));
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // Async I/O is not supported by this cached request wrapper
                throw new UnsupportedOperationException("Async read listeners are not supported by CachedBodyHttpServletRequest");
            }

            @Override
            public int read() throws IOException {
                return byteArrayInputStream.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new StringReader(body));
    }
}