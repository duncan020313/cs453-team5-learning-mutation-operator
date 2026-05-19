package astramut.experiment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

final class PitestXmlReport {
  MutationTotals parseMutationTotals(Path reportDir, Set<String> targetMethods)
      throws IOException, ParserConfigurationException, SAXException {
    MutationTotals totals = new MutationTotals();
    List<Path> xmls;
    try (var stream = Files.walk(reportDir)) {
      xmls = stream.filter(path -> path.getFileName().toString().equals("mutations.xml")).toList();
    }
    for (Path xml : xmls) {
      Document document =
          DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.toFile());
      NodeList mutations = document.getElementsByTagName("mutation");
      for (int i = 0; i < mutations.getLength(); i++) {
        Element mutation = (Element) mutations.item(i);
        if (!targetMethods.contains(childText(mutation, "mutatedMethod"))) {
          continue;
        }
        String status = mutation.getAttribute("status");
        String detected = mutation.getAttribute("detected");
        totals.incrementGenerated();
        if ("true".equalsIgnoreCase(detected)
            || "KILLED".equals(status)
            || "TIMED_OUT".equals(status)
            || "MEMORY_ERROR".equals(status)) {
          totals.incrementKilled();
        } else if ("NO_COVERAGE".equals(status)) {
          totals.incrementNoCoverage();
        } else {
          totals.incrementSurvived();
        }
      }
    }
    return totals;
  }

  void copyCombinedMutationXml(Path reportDir, Map<String, Set<String>> targetMethods)
      throws IOException, ParserConfigurationException, SAXException, TransformerException {
    Path combined = reportDir.resolve("mutations.xml");
    Document output = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    Element root = output.createElement("mutations");
    output.appendChild(root);

    List<Path> xmls;
    try (var stream = Files.walk(reportDir)) {
      xmls =
          stream
              .filter(path -> path.getFileName().toString().equals("mutations.xml"))
              .filter(path -> !path.equals(combined))
              .toList();
    }
    for (Path xml : xmls) {
      Document document =
          DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.toFile());
      NodeList mutations = document.getElementsByTagName("mutation");
      for (int i = 0; i < mutations.getLength(); i++) {
        Element mutation = (Element) mutations.item(i);
        String className = childText(mutation, "mutatedClass");
        String methodName = childText(mutation, "mutatedMethod");
        if (targetMethods.getOrDefault(className, Set.of()).contains(methodName)) {
          root.appendChild(output.importNode(mutation, true));
        }
      }
    }

    var transformer = TransformerFactory.newInstance().newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.transform(new DOMSource(output), new StreamResult(combined.toFile()));
  }

  private String childText(Element element, String tagName) {
    NodeList children = element.getElementsByTagName(tagName);
    if (children.getLength() == 0) {
      return "";
    }
    return children.item(0).getTextContent();
  }
}
