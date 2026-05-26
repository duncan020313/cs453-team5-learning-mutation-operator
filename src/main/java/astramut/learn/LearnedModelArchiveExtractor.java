package astramut.learn;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;

public final class LearnedModelArchiveExtractor {
  private static final int TAR_BLOCK_SIZE = 512;

  public Path extract(Path archive, String entryName, Path cacheDir) throws IOException {
    Files.createDirectories(cacheDir);
    Path output = cacheDir.resolve(safeName(entryName));
    if (Files.isRegularFile(output)
        && Files.getLastModifiedTime(output).compareTo(Files.getLastModifiedTime(archive)) >= 0) {
      return output;
    }

    Path temporary = Files.createTempFile(cacheDir, output.getFileName().toString(), ".tmp");
    try (InputStream input = new GZIPInputStream(Files.newInputStream(archive));
        OutputStream out = Files.newOutputStream(temporary)) {
      if (!copyEntry(input, entryName, out)) {
        throw new IOException("model entry not found in archive: " + entryName);
      }
    } catch (IOException e) {
      Files.deleteIfExists(temporary);
      throw e;
    }

    Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
    return output;
  }

  private boolean copyEntry(InputStream input, String expectedName, OutputStream output)
      throws IOException {
    byte[] header = new byte[TAR_BLOCK_SIZE];
    while (true) {
      if (!readBlock(input, header)) {
        return false;
      }
      if (isZeroBlock(header)) {
        return false;
      }

      String name = parseString(header, 0, 100);
      String prefix = parseString(header, 345, 155);
      String fullName = prefix.isBlank() ? name : prefix + "/" + name;
      long size = parseOctal(header, 124, 12);
      byte type = header[156];

      if ((type == 0 || type == '0') && expectedName.equals(fullName)) {
        copyBytes(input, output, size);
        skipPadding(input, size);
        return true;
      }
      skipBytes(input, size);
      skipPadding(input, size);
    }
  }

  private static boolean readBlock(InputStream input, byte[] block) throws IOException {
    int offset = 0;
    while (offset < block.length) {
      int read = input.read(block, offset, block.length - offset);
      if (read < 0) {
        if (offset == 0) {
          return false;
        }
        throw new EOFException("truncated tar header");
      }
      offset += read;
    }
    return true;
  }

  private static boolean isZeroBlock(byte[] block) {
    for (byte b : block) {
      if (b != 0) {
        return false;
      }
    }
    return true;
  }

  private static String parseString(byte[] block, int offset, int length) {
    int end = offset;
    int max = offset + length;
    while (end < max && block[end] != 0) {
      end++;
    }
    return new String(block, offset, end - offset, StandardCharsets.US_ASCII).trim();
  }

  private static long parseOctal(byte[] block, int offset, int length) {
    String value = parseString(block, offset, length).trim();
    if (value.isEmpty()) {
      return 0;
    }
    return Long.parseLong(value, 8);
  }

  private static void copyBytes(InputStream input, OutputStream output, long size)
      throws IOException {
    byte[] buffer = new byte[8192];
    long remaining = size;
    while (remaining > 0) {
      int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
      if (read < 0) {
        throw new EOFException("truncated tar entry");
      }
      output.write(buffer, 0, read);
      remaining -= read;
    }
  }

  private static void skipBytes(InputStream input, long size) throws IOException {
    long remaining = size;
    while (remaining > 0) {
      long skipped = input.skip(remaining);
      if (skipped <= 0) {
        if (input.read() < 0) {
          throw new EOFException("truncated tar entry");
        }
        skipped = 1;
      }
      remaining -= skipped;
    }
  }

  private static void skipPadding(InputStream input, long size) throws IOException {
    long padding = (TAR_BLOCK_SIZE - (size % TAR_BLOCK_SIZE)) % TAR_BLOCK_SIZE;
    skipBytes(input, padding);
  }

  private static String safeName(String entryName) {
    return entryName.replaceAll("[^A-Za-z0-9_.-]", "_");
  }
}
