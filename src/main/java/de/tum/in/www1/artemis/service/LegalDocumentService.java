package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

@Service
public class LegalDocumentService {

    private final Logger log = LoggerFactory.getLogger(LegalDocumentService.class);

    @Value("${artemis.legal-path}")
    private String legalDocumentsBasePath;

    private static final String LEGAL_DOCUMENTS_FILE_EXTENSION = ".md";

    /**
     * Returns the privacy statement if you want to update it.
     *
     * @param language the language of the privacy statement
     * @return the privacy statement that should be updated
     */
    public PrivacyStatement getPrivacyStatementForUpdate(LegalDocumentLanguage language) {
        return (PrivacyStatement) getLegalDocumentForUpdate(language, LegalDocumentType.PRIVACY_STATEMENT);
    }

    /**
     * Returns the imprint if you want to view it.
     *
     * @param language the language of the imprint
     * @return the imprint that should be updated
     */

    public Imprint getImprintForUpdate(LegalDocumentLanguage language) {
        return (Imprint) getLegalDocumentForUpdate(language, LegalDocumentType.IMPRINT);
    }

    /**
     * Returns the imprint if you want to view it.
     *
     * @param language the language of the imprint
     * @return the imprint to view
     */

    public Imprint getImprint(LegalDocumentLanguage language) {
        return (Imprint) getLegalDocument(language, LegalDocumentType.IMPRINT);
    }

    /**
     * Returns the privacy statement if you want to view it.
     *
     * @param language the language of the privacy statement
     * @return the privacy statement to view
     */

    public PrivacyStatement getPrivacyStatement(LegalDocumentLanguage language) {
        return (PrivacyStatement) getLegalDocument(language, LegalDocumentType.PRIVACY_STATEMENT);
    }

    /**
     * Updates the imprint
     *
     * @param imprint the imprint to update with the new content
     * @return the updated imprint
     */
    public Imprint updateImprint(Imprint imprint) {
        return (Imprint) updateLegalDocument(imprint);
    }

    /**
     * Updates the privacy statement
     *
     * @param privacyStatement the privacy statement to update with the new content
     * @return the updated privacy statement
     */

    public PrivacyStatement updatePrivacyStatement(PrivacyStatement privacyStatement) {
        return (PrivacyStatement) updateLegalDocument(privacyStatement);
    }

    /**
     * Returns the legal document if you want to update it.
     * If it currently doesn't exist an empty string is returned as it will be created once the user saves it.
     *
     * @param language the language of the legal document
     * @param type     the type of the legal document
     * @return the legal document with the given language and type
     */
    private LegalDocument getLegalDocumentForUpdate(LegalDocumentLanguage language, LegalDocumentType type) {
        String legalDocumentText = "";
        if (getLegalDocumentPath(language, type).isEmpty()) {
            switch (type) {
                case PRIVACY_STATEMENT -> {
                    return new PrivacyStatement(legalDocumentText, language);
                }
                case IMPRINT -> {
                    return new Imprint(legalDocumentText, language);
                }
                default -> {
                    throw new IllegalArgumentException("Legal document type not supported");
                }
            }

        }
        return readLegalDocument(language, type);

    }

    /**
     * Returns the legal document if you want to view it
     * If it currently doesn't exist in the given language, the other language is returned.
     * If it currently doesn't exist in any language, an exception is thrown.
     *
     * @param language the language of the legal document
     * @param type     the type of the legal document
     * @return the legal document with the given language and type
     */
    private LegalDocument getLegalDocument(LegalDocumentLanguage language, LegalDocumentType type) {
        // if it doesn't exist for one language, try to return the other language, and only throw an exception if it doesn't exist for both languages
        if (getLegalDocumentPath(LegalDocumentLanguage.GERMAN, type).isEmpty() && getLegalDocumentPath(LegalDocumentLanguage.ENGLISH, type).isEmpty()) {
            throw new BadRequestAlertException("Could not find " + type + " file for any language", type.name(), "noLegalDocumentFile");
        }
        else if (language == LegalDocumentLanguage.GERMAN && getLegalDocumentPath(language, type).isEmpty()) {
            language = LegalDocumentLanguage.ENGLISH;
        }
        else if (language == LegalDocumentLanguage.ENGLISH && getLegalDocumentPath(language, type).isEmpty()) {
            language = LegalDocumentLanguage.GERMAN;
        }

        return readLegalDocument(language, type);

    }

    private LegalDocument readLegalDocument(LegalDocumentLanguage language, LegalDocumentType type) {
        String legalDocumentText;
        try {
            legalDocumentText = Files.readString(getLegalDocumentPath(language, type).get());
        }
        catch (IOException e) {
            log.error("Could not read {} file for language {}", type, language);
            throw new InternalServerErrorException("Could not read " + type + " file for language " + language);
        }
        return type == LegalDocumentType.PRIVACY_STATEMENT ? new PrivacyStatement(legalDocumentText, language) : new Imprint(legalDocumentText, language);
    }

    protected LegalDocument updateLegalDocument(LegalDocument legalDocument) {
        if (legalDocument.getText().isBlank()) {
            throw new BadRequestAlertException("Legal document text cannot be empty", legalDocument.getType().name(), "emptyLegalDocument");
        }
        try {
            var legalDocumentsBasePathAsPath = Path.of(legalDocumentsBasePath);
            // If the directory, doesn't exist, we need to create the directory first, otherwise writeString fails.
            if (!Files.exists(legalDocumentsBasePathAsPath)) {
                Files.createDirectories(legalDocumentsBasePathAsPath);
            }
            Files.writeString(getLegalDocumentPath(legalDocument.getLanguage(), legalDocument.getType(), true).get(), legalDocument.getText(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        }
        catch (IOException e) {
            log.error("Could not update {} file for language {}", legalDocument.getType(), legalDocument.getLanguage());
            throw new InternalServerErrorException("Could not update " + legalDocument.getType() + " file for language " + legalDocument.getLanguage());
        }
        return legalDocument;
    }

    private Optional<Path> getLegalDocumentPath(LegalDocumentLanguage language, LegalDocumentType type, boolean isUpdate) {

        var filePath = getLegalDocumentPathForTypeAndLanguage(language, type);
        if (Files.exists(getLegalDocumentPathForTypeAndLanguage(language, type))) {
            return Optional.of(filePath);
        }
        // if it is an update, we need the path to create the file
        return isUpdate ? Optional.of(filePath) : Optional.empty();
    }

    private Optional<Path> getLegalDocumentPath(LegalDocumentLanguage language, LegalDocumentType type) {
        return getLegalDocumentPath(language, type, false);
    }

    private Path getLegalDocumentPathForTypeAndLanguage(LegalDocumentLanguage language, LegalDocumentType type) {
        return Path.of(legalDocumentsBasePath, type.getFileBaseName() + language.getShortName() + LEGAL_DOCUMENTS_FILE_EXTENSION);
    }

}
