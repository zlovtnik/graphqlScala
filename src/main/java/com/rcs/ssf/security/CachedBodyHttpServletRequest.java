package com.rcs.ssf.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * HttpServletRequest wrapper that caches the request body for multiple reads.
 * 
 * Enforces a maximum body size to prevent OOM attacks. Async I/O listeners are
 * not supported.
 */
@Slf4j
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private static final long DEFAULT_MAX_BODY_SIZE = 1_000_000L; // 1MB default

    private final byte[] cachedBytes;
    private final Charset bodyCharset;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        this(request, DEFAULT_MAX_BODY_SIZE);
    }

    /**
     * Creates a new cached request with a configurable maximum body size.
     *
     * Enforces the size limit on actual bytes read from the request input stream
     * before
     * decoding/accumulation. This prevents the StringBuilder from growing beyond
     * the allowed
     * size and avoids false rejections due to UTF-8 encoding overhead.
     *
     * @param request          the original HttpServletRequest
     * @param maxBodySizeBytes maximum allowed request body size in bytes
     * @throws IOException if the request body cannot be read or exceeds the size
     *                     limit
     */
    public CachedBodyHttpServletRequest(HttpServletRequest request, long maxBodySizeBytes) throws IOException {
        super(request);
        String encoding = request.getCharacterEncoding();
        // Handle charset with exception fallback for invalid names
        Charset resolvedCharset;
        if (encoding == null || encoding.isBlank()) {
            resolvedCharset = StandardCharsets.UTF_8;
        } else {
            try {
                resolvedCharset = Charset.forName(encoding);
            } catch (Exception e) {
                log.warn("Invalid charset '{}' in request; falling back to UTF-8: {}", encoding, e.getMessage());
                resolvedCharset = StandardCharsets.UTF_8;
            }
        }
        this.bodyCharset = resolvedCharset;

        // Read raw bytes from input stream with strict size enforcement
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        byte[] rawBuffer = new byte[8192];
        long totalBytesRead = 0L;
        int bytesRead;

        try (InputStream inputStream = request.getInputStream()) {
            while ((bytesRead = inputStream.read(rawBuffer)) > 0) {
                totalBytesRead += bytesRead;
                if (totalBytesRead > maxBodySizeBytes) {
                    throw new IOException(
                            "Request body exceeds maximum allowed size of " + maxBodySizeBytes + " bytes");
                }
                byteBuffer.write(rawBuffer, 0, bytesRead);
            }
        }

        // Cache the raw bytes
        cachedBytes = byteBuffer.toByteArray();
    }

    public String getBody() {
        return new String(cachedBytes, bodyCharset);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBytes);
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
                throw new UnsupportedOperationException(
                        "Async read listeners are not supported by CachedBodyHttpServletRequest");
            }

            @Override
            public int read() throws IOException {
                return byteArrayInputStream.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new StringReader(getBody()));
    }
}