import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Course } from 'app/entities/course.model';
import shortAnswerQuizTemplate from '../../../fixtures/exercise/quiz/short_answer/template.json';
import multipleChoiceQuizTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import { convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import { courseManagementRequest } from '../../../support/artemis';
import { admin, studentOne, tutor } from '../../../support/users';

describe('Quiz Exercise Assessment', () => {
    let course: Course;
    let quizExercise: QuizExercise;

    before('Create course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse().then((response) => {
            course = convertCourseAfterMultiPart(response);
            courseManagementRequest.addStudentToCourse(course, studentOne);
            courseManagementRequest.addTutorToCourse(course, tutor);
        });
    });

    describe('MC Quiz assessment', () => {
        before('Creates a quiz and a submission', () => {
            cy.login(admin);
            courseManagementRequest.createQuizExercise({ course }, [multipleChoiceQuizTemplate], undefined, undefined, 1).then((quizResponse) => {
                quizExercise = quizResponse.body;
                courseManagementRequest.setQuizVisible(quizExercise.id!);
                courseManagementRequest.startQuizNow(quizExercise.id!);
            });
        });

        it('Assesses a mc quiz submission automatically', () => {
            cy.login(studentOne);
            courseManagementRequest.startExerciseParticipation(quizExercise.id!);
            courseManagementRequest.createMultipleChoiceSubmission(quizExercise, [0, 2]);
            cy.visit('/courses/' + course.id + '/exercises/' + quizExercise.id);
            cy.reloadUntilFound('#submission-result-graded');
            cy.contains('50%').should('be.visible');
        });
    });

    describe('SA Quiz assessment', () => {
        before('Creates a quiz and a submission', () => {
            cy.login(admin);
            courseManagementRequest.createQuizExercise({ course }, [shortAnswerQuizTemplate], undefined, undefined, 1).then((quizResponse) => {
                quizExercise = quizResponse.body;
                courseManagementRequest.setQuizVisible(quizExercise.id!);
                courseManagementRequest.startQuizNow(quizExercise.id!);
            });
        });

        it('Assesses a sa quiz submission automatically', () => {
            cy.login(studentOne);
            courseManagementRequest.startExerciseParticipation(quizExercise.id!);
            courseManagementRequest.createShortAnswerSubmission(quizExercise, ['give', 'let', 'run', 'desert']);
            cy.visit('/courses/' + course.id + '/exercises/' + quizExercise.id);
            cy.reloadUntilFound('#submission-result-graded');
            cy.contains('66.7%').should('be.visible');
        });
    });

    after('Delete course', () => {
        if (course) {
            cy.login(admin);
            courseManagementRequest.deleteCourse(course.id!);
        }
    });
});
