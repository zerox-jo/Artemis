/*
 * This file configures the actual build steps for the automatic grading.
 *
 * !!!
 * For regular exercises, there is no need to make changes to this file.
 * Only this base configuration is actively supported by the Artemis maintainers
 * and/or your Artemis instance administrators.
 * !!!
 */

dockerImage = "#dockerImage"
dockerFlags = ""

isSolutionBuild = "${env.JOB_NAME}" ==~ /.+-SOLUTION$/
isTemplateBuild = "${env.JOB_NAME}" ==~ /.+-BASE$/

/**
 * Main function called by Jenkins.
 */
void testRunner() {
    docker.image(dockerImage).inside(dockerFlags) { c ->
        runTestSteps()
    }
}

private void runTestSteps() {
    test()
}

/**
 * Run unit tests
 */
private void test() {
    stage('Test') {
        sh "./gradlew clean test"
    }
}

/**
 * Runs the static code analysis
 */
private void staticCodeAnalysis() {
    stage("StaticCodeAnalysis") {
        sh """
        rm -rf staticCodeAnalysisReports
        mkdir staticCodeAnalysisReports
        ./gradlew check -x test
        cp target/spotbugsXml.xml staticCodeAnalysisReports || true
        cp target/checkstyle-result.xml staticCodeAnalysisReports || true
        cp target/pmd.xml staticCodeAnalysisReports || true
        cp target/cpd.xml staticCodeAnalysisReports || true
        """
    }
}

/**
 * Script of the post build tasks aggregating all JUnit files in $WORKSPACE/results.
 *
 * Called by Jenkins.
 */
void postBuildTasks() {
    if (#staticCodeAnalysisEnabled) {
        catchError {
            staticCodeAnalysis()
        }
    }
    sh 'rm -rf results'
    sh 'mkdir results'
    sh 'cp build/test-results/test/*.xml $WORKSPACE/results/ || true'
}

// very important, do not remove
// required so that Jenkins finds the methods defined in this script
return this
