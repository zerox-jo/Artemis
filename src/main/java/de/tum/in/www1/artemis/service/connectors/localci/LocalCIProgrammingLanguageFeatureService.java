package de.tum.in.www1.artemis.service.connectors.localci;

import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.*;
import static de.tum.in.www1.artemis.domain.enumeration.ProjectType.*;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;

@Service
@Profile("localci")
public class LocalCIProgrammingLanguageFeatureService extends ProgrammingLanguageFeatureService {

    public LocalCIProgrammingLanguageFeatureService() {
        // Must be extended once a new programming language is added
        programmingLanguageFeatures.put(EMPTY, new ProgrammingLanguageFeature(EMPTY, false, false, false, false, false, List.of(), false, false));
        programmingLanguageFeatures.put(JAVA, new ProgrammingLanguageFeature(JAVA, false, false, true, true, false, List.of(PLAIN_GRADLE, GRADLE_GRADLE), false, false));
        // Local CI is not supporting Python at the moment.
        // Local CI is not supporting C at the moment.
        // Local CI is not supporting Haskell at the moment.
        // Local CI is not supporting Kotlin at the moment.
        // Local CI is not supporting VHDL at the moment.
        // Local CI is not supporting Assembler at the moment.
        // Local CI is not supporting Swift at the moment.
        // Local CI is not supporting OCAML at the moment.
    }
}
