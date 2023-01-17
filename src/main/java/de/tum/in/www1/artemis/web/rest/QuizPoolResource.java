package de.tum.in.www1.artemis.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.exam.quiz.QuizPool;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.exam.QuizPoolService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing QuizPool.
 */
@RestController
@RequestMapping("/api")
public class QuizPoolResource {

    private static final String ENTITY_NAME = "quizPool";

    private final Logger log = LoggerFactory.getLogger(QuizPoolResource.class);

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final QuizPoolService quizPoolService;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    public QuizPoolResource(QuizPoolService quizPoolService, CourseRepository courseRepository, AuthorizationCheckService authCheckService) {
        this.quizPoolService = quizPoolService;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * PUT /courses/{courseId}/exams/{examId}/quiz-pools : Update an existing QuizPool.
     *
     * @param courseId the id of the Course of which the QuizPool belongs to
     * @param examId   the id of the Exam of which the QuizPool belongs to
     * @param quizPool the QuizPool to update
     * @return the ResponseEntity with status 200 (OK) and with the body of the QuizPool, or with status 400 (Bad Request) if the QuizPool is invalid
     */
    @PutMapping("/courses/{courseId}/exams/{examId}/quiz-pools")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<QuizPool> updateQuizPool(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody QuizPool quizPool) {
        log.info("REST request to update QuizPool : {}", quizPool);

        validateCourseRole(courseId);
        quizPool = quizPoolService.update(examId, quizPool);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, quizPool.getId().toString())).body(quizPool);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/quiz-pools : Get an existing QuizPool.
     *
     * @param courseId the id of the Course of which the QuizPool belongs to
     * @param examId   the id of the Exam of which the QuizPool belongs to
     * @return the ResponseEntity with status 200 (OK) and with the body of the QuizPool, or with status 404 (Not Found) if the QuizPool is not found
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/quiz-pools")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<QuizPool> getQuizPool(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to get QuizPool given examId : {}", examId);

        validateCourseRole(courseId);
        QuizPool quizPool = quizPoolService.findByExamId(examId);

        return ResponseEntity.ok().body(quizPool);
    }

    private void validateCourseRole(Long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);
        // examAccessService.checkCourseAccessForInstructorElseThrow(courseId);
    }
}