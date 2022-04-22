import { MockRouter } from '../../../../helpers/mocks/mock-router';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { Router } from '@angular/router';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { of } from 'rxjs';
import { StudentExam } from 'app/entities/student-exam.model';

describe('Student Exam Service', () => {
    let httpClient: any;
    let httpClientPutSpy: any;
    let service: StudentExamService;
    let accountService: AccountService;

    beforeEach(() => {
        httpClient = {
            put: jest.fn(),
            get: jest.fn(),
            patch: jest.fn(),
        };
        accountService = {
            setAccessRightsForCourse: jest.fn(),
        } as any as AccountService;
        httpClientPutSpy = jest.spyOn(httpClient, 'put');
        service = new StudentExamService(new MockRouter() as any as Router, httpClient as HttpClient, accountService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should call correct url if toggling submitted state and unsubmit is false', () => {
        service.toggleSubmittedState(1, 2, 3, false);
        expect(httpClientPutSpy).toHaveBeenCalledTimes(1);
        expect(httpClientPutSpy.mock.calls[0]).toHaveLength(3);
        expect(httpClientPutSpy.mock.calls[0][0]).toContain('toggle-to-submitted');
    });

    it('should call correct url if toggling submitted state and unsubmit is true', () => {
        service.toggleSubmittedState(1, 2, 3, true);
        expect(httpClientPutSpy).toHaveBeenCalledTimes(1);
        expect(httpClientPutSpy.mock.calls[0]).toHaveLength(3);
        expect(httpClientPutSpy.mock.calls[0][0]).toContain('toggle-to-unsubmitted');
    });

    it.each([
        {
            exam: {
                course: {
                    id: 1,
                },
            },
        },
        {
            exam: {
                course: undefined,
            },
        },
        {
            exam: undefined,
        },
        undefined,
    ])('should fetch and process an exam correctly on find and updateWorkingTime', (payload) => {
        const response = new HttpResponse<StudentExam>({ body: payload });
        const getSpy = jest.spyOn(httpClient, 'get').mockReturnValue(of(response));

        let returnedExam;
        service.find(1, 2, 3).subscribe((result) => (returnedExam = result));

        expect(getSpy).toHaveBeenCalledTimes(1);
        expect(getSpy).toHaveBeenCalledWith(`${SERVER_API_URL}api/courses/1/exams/2/student-exams/3`, { observe: 'response' });
        expect(returnedExam).toBe(response);
        expect(accountService.setAccessRightsForCourse).toHaveBeenCalledTimes(payload?.exam?.course ? 1 : 0);

        const patchSpy = jest.spyOn(httpClient, 'patch').mockReturnValue(of(response));
        service.updateWorkingTime(1, 2, 3, 10).subscribe((result) => (returnedExam = result));

        expect(patchSpy).toHaveBeenCalledTimes(1);
        expect(patchSpy).toHaveBeenCalledWith(`${SERVER_API_URL}api/courses/1/exams/2/student-exams/3/working-time`, 10, { observe: 'response' });
        expect(returnedExam).toBe(response);
        expect(accountService.setAccessRightsForCourse).toHaveBeenCalledTimes(payload?.exam?.course ? 2 : 0);
    });

    it('should fetch and process exams correctly on findAllForExam', () => {
        const payload = [
            {
                exam: {
                    course: {
                        id: 1,
                    },
                },
            },
            {
                exam: {
                    course: {
                        id: 1,
                    },
                },
            },
            {
                exam: {
                    course: undefined,
                },
            },
            {
                exam: undefined,
            },
        ] as StudentExam[];
        const response = new HttpResponse<StudentExam[]>({ body: payload });
        const getSpy = jest.spyOn(httpClient, 'get').mockReturnValue(of(response));

        let returnedExams;
        service.findAllForExam(1, 2).subscribe((result) => (returnedExams = result));

        expect(getSpy).toHaveBeenCalledTimes(1);
        expect(getSpy).toHaveBeenCalledWith(`${SERVER_API_URL}api/courses/1/exams/2/student-exams`, { observe: 'response' });
        expect(returnedExams).toBe(response);
        expect(accountService.setAccessRightsForCourse).toHaveBeenCalledTimes(2);
    });
});
