import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { Result } from 'app/entities/result.model';
import { ArtemisTestModule } from '../../../test.module';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { cloneDeep } from 'lodash-es';
import { Submission } from 'app/entities/submission.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { faCheckCircle, faQuestionCircle, faTimesCircle } from '@fortawesome/free-regular-svg-icons';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MissingResultInformation, ResultTemplateStatus } from 'app/exercises/shared/result/result.utils';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { SimpleChange } from '@angular/core';

describe('ResultComponent', () => {
    let fixture: ComponentFixture<ResultComponent>;
    let component: ResultComponent;

    const result: Result = { id: 0, participation: {}, submission: {} };
    const programmingExercise: ProgrammingExercise = {
        id: 1,
        type: ExerciseType.PROGRAMMING,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
    };
    const programmingParticipation: ProgrammingExerciseStudentParticipation = { id: 2, type: ParticipationType.PROGRAMMING, exercise: programmingExercise };

    const modelingExercise: ModelingExercise = {
        id: 3,
        type: ExerciseType.MODELING,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
    };
    const modelingParticipation: StudentParticipation = { id: 4, type: ParticipationType.STUDENT, exercise: modelingExercise };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockDirective(NgbTooltip)],
            declarations: [ResultComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisTimeAgoPipe), MockPipe(ArtemisDatePipe)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ResultComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        component.result = result;
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should set results for programming exercise', () => {
        const submission1: Submission = { id: 1 };
        const result1: Result = { id: 1, submission: submission1, score: 1 };
        const result2: Result = { id: 2 };
        const participation1 = cloneDeep(programmingParticipation);
        participation1.results = [result1, result2];
        component.participation = participation1;

        fixture.detectChanges();

        expect(component.result).toEqual(result1);
        expect(component.result!.participation).toEqual(participation1);
        expect(component.submission).toEqual(submission1);
        expect(component.textColorClass).toBe('text-secondary');
        expect(component.resultIconClass).toEqual(faQuestionCircle);
        expect(component.resultString).toBe('artemisApp.result.resultString.programming (artemisApp.result.preliminary)');
    });

    it('should set results for modeling exercise', () => {
        const submission1: Submission = { id: 1 };
        const result1: Result = { id: 1, submission: submission1, score: 1 };
        const result2: Result = { id: 2 };
        const participation1 = cloneDeep(modelingParticipation);
        participation1.results = [result1, result2];
        component.participation = participation1;

        fixture.detectChanges();

        expect(component.result).toEqual(result1);
        expect(component.result!.participation).toEqual(participation1);
        expect(component.submission).toEqual(submission1);
        expect(component.textColorClass).toBe('text-danger');
        expect(component.resultIconClass).toEqual(faTimesCircle);
        expect(component.resultString).toBe('artemisApp.result.resultString.nonProgramming');
        expect(component.templateStatus).toBe(ResultTemplateStatus.HAS_RESULT);
    });

    it('should set results for quiz exam exercise', () => {
        component.participation = undefined;
        component.isQuizExam = true;
        component.result = { id: 1, score: 100 } as Result;
        fixture.detectChanges();
        expect(component.templateStatus).toEqual(ResultTemplateStatus.HAS_RESULT);
        expect(component.textColorClass).toBe('text-success');
        expect(component.resultIconClass).toBe(faCheckCircle);
        expect(component.resultString).toBe('artemisApp.result.resultString.nonProgramming');
    });

    describe('ngOnChanges', () => {
        it('should call ngOnInit when participation changes', () => {
            const changes = {
                participation: new SimpleChange(undefined, new StudentParticipation(), false),
            };
            const ngOnInitSpy = jest.spyOn(component, 'ngOnInit').mockImplementation();
            component.ngOnChanges(changes);
            expect(ngOnInitSpy).toHaveBeenCalledOnce();
        });
        it('should call ngOnInit when result changes', () => {
            const changes = {
                result: new SimpleChange(undefined, new Result(), false),
            };
            const ngOnInitSpy = jest.spyOn(component, 'ngOnInit').mockImplementation();
            component.ngOnChanges(changes);
            expect(ngOnInitSpy).toHaveBeenCalledOnce();
        });
        it('should call evaluate when missingResultInfo changes', () => {
            const changes = {
                missingResultInfo: new SimpleChange(undefined, MissingResultInformation.FAILED_PROGRAMMING_SUBMISSION_ONLINE_IDE, false),
            };
            const evaluateSpy = jest.spyOn(component, 'evaluate').mockImplementation();
            component.ngOnChanges(changes);
            expect(evaluateSpy).toHaveBeenCalledOnce();
        });
        it('should set templateStatus when isBuilding changes', () => {
            component.templateStatus = ResultTemplateStatus.NO_RESULT;
            const changes = {
                isBuilding: new SimpleChange(undefined, true, false),
            };
            component.ngOnChanges(changes);
            component.templateStatus = ResultTemplateStatus.IS_BUILDING;
        });
        it('should call evaluate when isBuilding changes to undefined', () => {
            component.templateStatus = ResultTemplateStatus.NO_RESULT;
            const changes = {
                isBuilding: new SimpleChange(true, undefined, false),
            };
            const evaluateSpy = jest.spyOn(component, 'evaluate').mockImplementation();
            component.ngOnChanges(changes);
            expect(evaluateSpy).toHaveBeenCalledOnce();
        });
    });
});
