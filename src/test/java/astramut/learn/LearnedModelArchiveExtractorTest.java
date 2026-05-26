package astramut.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LearnedModelArchiveExtractorTest {
  @TempDir Path tempDir;

  @Test
  void extractsRequestedModelEntryIntoCache() throws IOException {
    Path archive = tempDir.resolve("model.tar.gz");
    writeTarGz(
        archive,
        new TarEntry("learned/README.md", "readme"),
        new TarEntry("learned/patterns-full.json", "{\"runs\":[]}"));

    LearnedModelArchiveExtractor extractor = new LearnedModelArchiveExtractor();

    Path extracted =
        extractor.extract(archive, "learned/patterns-full.json", tempDir.resolve("cache"));

    assertEquals("{\"runs\":[]}", Files.readString(extracted, StandardCharsets.UTF_8));
    assertEquals("learned_patterns-full.json", extracted.getFileName().toString());
  }

  @Test
  void failsWhenEntryIsMissing() throws IOException {
    Path archive = tempDir.resolve("model.tar.gz");
    writeTarGz(archive, new TarEntry("other.json", "{}"));

    LearnedModelArchiveExtractor extractor = new LearnedModelArchiveExtractor();

    assertThrows(
        IOException.class,
        () -> extractor.extract(archive, "learned/patterns-full.json", tempDir.resolve("cache")));
  }

  private static void writeTarGz(Path archive, TarEntry... entries) throws IOException {
    ByteArrayOutputStream tar = new ByteArrayOutputStream();
    for (TarEntry entry : entries) {
      byte[] content = entry.content().getBytes(StandardCharsets.UTF_8);
      tar.write(header(entry.name(), content.length));
      tar.write(content);
      int padding = (512 - (content.length % 512)) % 512;
      tar.write(new byte[padding]);
    }
    tar.write(new byte[1024]);

    try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(archive))) {
      gzip.write(tar.toByteArray());
    }
  }

  private static byte[] header(String name, int size) {
    byte[] header = new byte[512];
    writeAscii(header, 0, 100, name);
    writeOctal(header, 100, 8, 0644);
    writeOctal(header, 108, 8, 0);
    writeOctal(header, 116, 8, 0);
    writeOctal(header, 124, 12, size);
    writeOctal(header, 136, 12, 0);
    for (int i = 148; i < 156; i++) {
      header[i] = ' ';
    }
    header[156] = '0';
    writeAscii(header, 257, 6, "ustar");
    writeAscii(header, 263, 2, "00");

    int checksum = 0;
    for (byte b : header) {
      checksum += b & 0xff;
    }
    writeOctal(header, 148, 8, checksum);
    return header;
  }

  private static void writeAscii(byte[] target, int offset, int length, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(bytes, 0, target, offset, Math.min(length, bytes.length));
  }

  private static void writeOctal(byte[] target, int offset, int length, long value) {
    String octal = Long.toOctalString(value);
    String padded = "0".repeat(Math.max(0, length - octal.length() - 1)) + octal;
    writeAscii(target, offset, length - 1, padded);
    target[offset + length - 1] = 0;
  }

  private record TarEntry(String name, String content) {}
}
