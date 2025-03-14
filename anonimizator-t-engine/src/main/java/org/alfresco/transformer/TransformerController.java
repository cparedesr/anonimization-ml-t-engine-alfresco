package org.alfresco.transformer;

import org.alfresco.transformer.AbstractTransformerController;
import org.alfresco.transformer.probes.ProbeTestTransform;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

@Controller
public class TransformerController extends AbstractTransformerController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformerController.class);
    private final RestTemplate restTemplate;

    private static final String ANONYMIZER_SERVICE_URL = "http://anonimizator-ml:8000/anonimizar-pdf";

    @Autowired
    public TransformerController() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String getTransformerName() {
        return "pdf-anonimizer";
    }

    @Override
    public String version() {
        return "1.0";
    }

    @Override
    public ProbeTestTransform getProbeTestTransform() {
        return new ProbeTestTransform(this, "quick.pdf", "quick-anonimized.pdf", 60, 16, 400, 10240, 1801, 920) {
            @Override
            protected void executeTransformCommand(File sourceFile, File targetFile) {
                transformImpl("pdf-anonimizer", "application/pdf", "application/pdf", null, sourceFile, targetFile);
            }
        };
    }



    @Override
    public void transformImpl(String transformName, String sourceMimetype, String targetMimetype,
                          Map<String, String> transformOptions, File sourceFile, File targetFile) {
    try {
        byte[] pdfBytes = Files.readAllBytes(sourceFile.toPath());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return sourceFile.getName();
            }
        });

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                ANONYMIZER_SERVICE_URL,
                HttpMethod.POST,
                requestEntity,
                byte[].class
        );

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {

            PDDocument document = PDDocument.load(response.getBody());
            String metadata = transformOptions.get("metadata");
            
            if (metadata != null && !metadata.isEmpty()) {
                PDDocumentCatalog catalog = document.getDocumentCatalog();
                PDMetadata xmpMetadata = new PDMetadata(document);
                
                xmpMetadata.importXMPMetadata(metadata.getBytes(StandardCharsets.UTF_8));
                catalog.setMetadata(xmpMetadata);
            }

            document.save(targetFile);
            document.close();

        } else {
            LOGGER.error("Error: Anonymizer service failed with status code: {}", response.getStatusCode());
            throw new RuntimeException("Anonymization failed: HTTP " + response.getStatusCode());
        }

    } catch (Exception e) {
        LOGGER.error("Error during anonymization transformation", e);
        throw new RuntimeException("Transformation error: " + e.getMessage(), e);
        }
    }
}
