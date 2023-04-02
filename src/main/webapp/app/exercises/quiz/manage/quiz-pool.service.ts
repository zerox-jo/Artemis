import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { QuizPool } from 'app/entities/quiz/quiz-pool.model';

export type EntityResponseType = HttpResponse<QuizExercise>;
export type EntityArrayResponseType = HttpResponse<QuizExercise[]>;

@Injectable({ providedIn: 'root' })
export class QuizPoolService {
    constructor(private http: HttpClient) {}

    update(courseId: number, examId: number, quizPool: QuizPool, req?: any): Observable<HttpResponse<QuizPool>> {
        const options = createRequestOption(req);
        return this.http.put<QuizPool>(`${SERVER_API_URL}api/courses/${courseId}/exams/${examId}/quiz-pools`, quizPool, { params: options, observe: 'response' });
    }

    find(courseId: number, examId: number): Observable<HttpResponse<QuizPool>> {
        return this.http.get<QuizPool>(`${SERVER_API_URL}api/courses/${courseId}/exams/${examId}/quiz-pools`, { observe: 'response' });
    }
}