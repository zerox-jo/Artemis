import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BuildPlan } from 'app/entities/build-plan.model';

export type EntityResponseType = HttpResponse<BuildPlan>;

@Injectable({ providedIn: 'root' })
export class BuildPlanService {
    public resourceUrl = `${SERVER_API_URL}api/programming-exercises`;

    constructor(private http: HttpClient) {}

    getBuildPlan(programmingExerciseId: number): Observable<EntityResponseType> {
        console.log(`${this.resourceUrl}/${programmingExerciseId}/build-plan`);
        return this.http.get<BuildPlan>(`${this.resourceUrl}/${programmingExerciseId}/build-plan`, { observe: 'response' });
    }

    putBuildPlan(programmingExerciseId: number, buildPlan: BuildPlan): Observable<EntityResponseType> {
        return this.http.put<BuildPlan>(`${this.resourceUrl}/${programmingExerciseId}/build-plan`, buildPlan, { observe: 'response' });
    }
}
