package at.favre.lib.bytes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Collection of additional {@link BytesTransformer} for more specific use cases
 */
public final class BytesTransformers {

    private BytesTransformers() {
    }

    /**
     * Create a {@link BytesTransformer} which appends 4 byte Crc32 checksum to given bytes
     *
     * @return transformer
     */
    public static BytesTransformer checksumAppendCrc32() {
        return new ChecksumTransformer(new CRC32(), ChecksumTransformer.Mode.APPEND, 4);
    }

    /**
     * Create a {@link BytesTransformer} which transforms to 4 byte Crc32 checksum of given bytes
     *
     * @return transformer
     */
    public static BytesTransformer checksumCrc32() {
        return new ChecksumTransformer(new CRC32(), ChecksumTransformer.Mode.TRANSFORM, 4);
    }

    /**
     * Create a {@link BytesTransformer} which transforms to 4 byte Crc32 checksum of given bytes
     *
     * @param checksum           used algorithm
     * @param mode               mode (append or convert)
     * @param checksumLengthByte the byte length of the checksum; the {@link Checksum} class always returns 8 byte, but some
     *                           checksum algorithms (e.g. CRC32) only require smaller output. Must  be between 1 and 8 byte.
     * @return transformer
     */
    public static BytesTransformer checksum(Checksum checksum, ChecksumTransformer.Mode mode, int checksumLengthByte) {
        return new ChecksumTransformer(checksum, mode, checksumLengthByte);
    }

    /**
     * Create a {@link BytesTransformer} which gzip compresses the internal byte array
     *
     * @return transformer
     * @throws IllegalStateException if compression was not possible (i.e. wrapped {@link java.io.IOException})
     * @see <a href="https://en.wikipedia.org/wiki/Gzip">Gzip</a>
     */
    public static BytesTransformer compressGzip() {
        return new GzipCompressor(true);
    }

    /**
     * Create a {@link BytesTransformer} which gzip decompresses the internal byte array
     *
     * @return transformer
     * @throws IllegalStateException if compression was not possible (i.e. wrapped {@link java.io.IOException})
     * @see <a href="https://en.wikipedia.org/wiki/Gzip">Gzip</a>
     */
    public static BytesTransformer decompressGzip() {
        return new GzipCompressor(false);
    }

    /**
     * Adds or converts to arbitrary checksum
     */
    final static class ChecksumTransformer implements BytesTransformer {
        enum Mode {
            /**
             * Appends checksum to given byte array
             */
            APPEND,
            /**
             * Transforms byte array and returns only checksum
             */
            TRANSFORM
        }

        private final Checksum checksum;
        private final Mode mode;
        private final int checksumLengthByte;

        public ChecksumTransformer(Checksum checksum, Mode mode, int checksumLengthByte) {
            if (checksumLengthByte <= 0 || checksumLengthByte > 8)
                throw new IllegalArgumentException("checksumlength must be between 1 and 8 bytes");

            Objects.requireNonNull(checksum, "checksum instance must not be null");
            this.checksum = checksum;
            this.mode = mode;
            this.checksumLengthByte = checksumLengthByte;
        }

        @Override
        public byte[] transform(byte[] currentArray, boolean inPlace) {
            checksum.update(currentArray, 0, currentArray.length);
            byte[] checksumBytes = Bytes.from(checksum.getValue()).resize(checksumLengthByte).array();

            if (mode == Mode.TRANSFORM) {
                return checksumBytes;
            } else {
                return Bytes.from(currentArray, checksumBytes).array();
            }
        }
    }

    /**
     * Byte compression with gzip
     */
    final static class GzipCompressor implements BytesTransformer {
        private final boolean compress;

        public GzipCompressor(boolean compress) {
            this.compress = compress;
        }

        @Override
        public byte[] transform(byte[] currentArray, boolean inPlace) {
            return compress ? compress(currentArray) : decompress(currentArray);
        }

        private byte[] decompress(byte[] compressedContent) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            GZIPInputStream gzipInputStream = null;
            byte[] returnBuffer;
            try {
                int len;
                byte buffer[] = new byte[4 * 1024];
                gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(compressedContent));

                while ((len = gzipInputStream.read(buffer)) > 0) {
                    bos.write(buffer, 0, len);
                }

                gzipInputStream.close();
                returnBuffer = bos.toByteArray();
                bos.close();
                return returnBuffer;
            } catch (Exception e) {
                throw new IllegalStateException("could not decompress gzip", e);
            }
        }

        private byte[] compress(byte[] content) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(content.length);
            GZIPOutputStream gzipOutputStream = null;
            byte[] returnBuffer;
            try {
                gzipOutputStream = new GZIPOutputStream(bos);
                gzipOutputStream.write(content);
                gzipOutputStream.close();
                returnBuffer = bos.toByteArray();
                bos.close();
                return returnBuffer;
            } catch (Exception e) {
                throw new IllegalStateException("could not compress gzip", e);
            }
        }
    }
}