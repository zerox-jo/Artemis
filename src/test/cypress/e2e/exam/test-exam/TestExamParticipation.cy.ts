import { Interception } from 'cypress/types/net-stubbing';
import dayjs from 'dayjs/esm';

import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';

import allSuccessful from '../../../fixtures/exercise/programming/all_successful/submission.json';
import buildError from '../../../fixtures/exercise/programming/build_error/submission.json';
import { examExerciseGroupCreation, examNavigation, examParticipation, examStartEnd } from '../../../support/artemis';
import { EXERCISE_TYPE } from '../../../support/constants';
import { Exercise } from '../../../support/pageobjects/exam/ExamParticipation';
import { courseManagementRequest } from '../../../support/requests/ArtemisRequests';
import { CypressExamBuilder, convertCourseAfterMultiPart } from '../../../support/requests/CourseManagementRequests';
import { admin, studentOne, studentThree, studentTwo } from '../../../support/users';
import { generateUUID } from '../../../support/utils';

// Common primitives
const textFixture = 'loremIpsum.txt';
let exerciseArray: Array<Exercise> = [];

describe('Test exam participation', () => {
    let course: Course;

    before('Create course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse(true).then((response) => {
            course = convertCourseAfterMultiPart(response);
        });
    });

    describe('Early Hand-in', () => {
        let exam: Exam;
        const examTitle = 'test-exam' + generateUUID();

        before('Create test exam', () => {
            cy.login(admin);
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .testExam()
                .visibleDate(dayjs().subtract(3, 'days'))
                .startDate(dayjs().subtract(2, 'days'))
                .endDate(dayjs().add(3, 'days'))
                .examMaxPoints(100)
                .numberOfExercises(10)
                .correctionRounds(0)
                .build();
            courseManagementRequest.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                examExerciseGroupCreation.addGroupWithExercise(exerciseArray, exam, EXERCISE_TYPE.Text, { textFixture });
                examExerciseGroupCreation.addGroupWithExercise(exerciseArray, exam, EXERCISE_TYPE.Text, { textFixture });
                examExerciseGroupCreation.addGroupWithExercise(exerciseArray, exam, EXERCISE_TYPE.Text, { textFixture });

                examExerciseGroupCreation.addGroupWithExercise(exerciseArray, exam, EXERCISE_TYPE.Programming, { submission: allSuccessful, expectedScore: 100 });
                examExerciseGroupCreation.addGroupWithExercise(exerciseArray, exam, EXERCISE_TYPE.Programming, { submission: buildError, expectedScore: 0 });

                examExerciseGroupCreation.addGroupWithExercise(exerciseArray, exam, EXERCISE_TYPE.Quiz, { quizExerciseID: 0 });
                examExerciseGroupCreation.addGroupWithExercise(exerciseArray, exam, EXERCISE_TYPE.Quiz, { quizExerciseID: 0 });

                examExerciseGroupCreation.addGroupWithExercise(exerciseArray, exam, EXERCISE_TYPE.Modeling);
                examExerciseGroupCreation.addGroupWithExercise(exerciseArray, exam, EXERCISE_TYPE.Modeling);
            });
        });

        it('Participates as a student in a registered test exam', () => {
            examParticipation.startParticipation(studentOne, course, exam);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                examNavigation.openExerciseAtIndex(j);
                examParticipation.makeSubmission(exercise.id, exercise.type, exercise.additionalData);
            }
            examParticipation.handInEarly();
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                examParticipation.verifyExerciseTitleOnFinalPage(exercise.id, exercise.title);
                if (exercise.type === EXERCISE_TYPE.Text) {
                    examParticipation.verifyTextExerciseOnFinalPage(exercise.additionalData!.textFixture!);
                }
            }
            examParticipation.checkExamTitle(examTitle);
        });

        it('Using save and continue to navigate within exam', () => {
            examParticipation.startParticipation(studentTwo, course, exam);
            examNavigation.openExerciseAtIndex(0);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                // Skip programming exercise this time to save execution time
                // (we also need to use the navigation bar here, since programming  exercises do not have a "Save and continue" button)
                if (exercise.type == EXERCISE_TYPE.Programming) {
                    examNavigation.openExerciseAtIndex(j + 1);
                } else {
                    examParticipation.checkExerciseTitle(exerciseArray[j].id, exerciseArray[j].title);
                    examParticipation.makeSubmission(exercise.id, exercise.type, exercise.additionalData);
                    examParticipation.clickSaveAndContinue();
                }
            }
            examParticipation.handInEarly();
        });

        it('Using exercise overview to navigate within exam', () => {
            examParticipation.startParticipation(studentThree, course, exam);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                // Skip programming exercise this time to save execution time
                // (we also need to use the navigation bar here, since programming  exercises do not have a "Save and continue" button)
                if (exercise.type == EXERCISE_TYPE.Programming) {
                    continue;
                } else {
                    examNavigation.openExerciseOverview();
                    examParticipation.selectExerciseOnOverview(j + 1);
                    examParticipation.checkExerciseTitle(exerciseArray[j].id, exerciseArray[j].title);
                    examParticipation.makeSubmission(exercise.id, exercise.type, exercise.additionalData);
                }
            }
            examParticipation.handInEarly();
        });
    });

    describe('Normal Hand-in', () => {
        let exam: Exam;
        const examTitle = 'exam' + generateUUID();

        before('Create exam', () => {
            exerciseArray = [];

            cy.login(admin);
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .testExam()
                .visibleDate(dayjs().subtract(3, 'days'))
                .startDate(dayjs().subtract(2, 'days'))
                .endDate(dayjs().add(3, 'days'))
                .workingTime(15)
                .examMaxPoints(10)
                .numberOfExercises(1)
                .build();
            courseManagementRequest.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                examExerciseGroupCreation.addGroupWithExercise(exerciseArray, exam, EXERCISE_TYPE.Text, { textFixture });
            });
        });

        it('Participates as a student in a registered exam', () => {
            examParticipation.startParticipation(studentOne, course, exam);
            const textExerciseIndex = 0;
            const textExercise = exerciseArray[textExerciseIndex];
            examNavigation.openExerciseAtIndex(textExerciseIndex);
            examParticipation.makeSubmission(textExercise.id, textExercise.type, textExercise.additionalData);
            examParticipation.clickSaveAndContinue();
            cy.get('#fullname', { timeout: 20000 }).should('be.visible');
            examStartEnd.finishExam().then((request: Interception) => {
                expect(request.response!.statusCode).to.eq(200);
            });
            examParticipation.verifyTextExerciseOnFinalPage(textExercise.additionalData!.textFixture!);
            examParticipation.checkExamTitle(examTitle);
        });
    });

    after('Delete course', () => {
        if (course) {
            cy.login(admin);
            courseManagementRequest.deleteCourse(course.id!);
        }
    });
});