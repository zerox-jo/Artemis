package de.tum.in.www1.artemis.localvcci;

import static de.tum.in.www1.artemis.util.ModelFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.util.LocalRepository;

/**
 * This class contains integration tests for edge cases pertaining to the local VC system.
 */
class LocalVCIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private LocalRepository assignmentRepository;

    @BeforeEach
    void initRepository() throws GitAPIException, IOException, URISyntaxException {
        // Create remote assignment repository
        Path remoteAssignmentRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, assignmentRepositorySlug);
        assignmentRepository = new LocalRepository(defaultBranch);
        assignmentRepository.configureRepos("localAssignment", remoteAssignmentRepositoryFolder);
    }

    @AfterEach
    void removeRepositories() {
        localVCLocalCITestService.removeRepository(assignmentRepository);
    }

    @Test
    void testFetchPush_repositoryDoesNotExist() throws IOException, GitAPIException, URISyntaxException {
        // Create a new repository, delete the remote repository and try to fetch and push to the remote repository.
        String projectKey = "SOMEPROJECTKEY";
        String repositorySlug = "some-repository-slug";
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, repositorySlug);
        LocalRepository someRepository = new LocalRepository(defaultBranch);
        someRepository.configureRepos("localRepository", remoteRepositoryFolder);

        // Delete the remote repository.
        someRepository.originGit.close();
        FileUtils.deleteDirectory(someRepository.originRepoFile);

        // Try to fetch from the remote repository.
        localVCLocalCITestService.testFetchThrowsException(someRepository.localGit, student1Login, USER_PASSWORD, projectKey, repositorySlug, InvalidRemoteException.class, "");

        // Try to push to the remote repository.
        localVCLocalCITestService.testPushThrowsException(someRepository.localGit, student1Login, projectKey, repositorySlug, notFound);

        // Cleanup
        someRepository.localGit.close();
        FileUtils.deleteDirectory(someRepository.localRepoFile);
    }

    @Test
    void testFetchPush_wrongCredentials() {
        // Try to access with the wrong password.
        localVCLocalCITestService.testFetchThrowsException(assignmentRepository.localGit, student1Login, "wrong-password", projectKey1, assignmentRepositorySlug, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, student1Login, "wrong-password", projectKey1, assignmentRepositorySlug, notAuthorized);

        // Try to access without a password.
        localVCLocalCITestService.testFetchThrowsException(assignmentRepository.localGit, student1Login, "", projectKey1, assignmentRepositorySlug, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, student1Login, "", projectKey1, assignmentRepositorySlug, notAuthorized);
    }

    @Test
    void testFetchPush_programmingExerciseDoesNotExist() throws GitAPIException, IOException, URISyntaxException {
        // Create a repository for an exercise that does not exist.
        String projectKey = "SOMEPROJECTKEY";
        String repositorySlug = "someprojectkey-some-repository-slug";
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey, repositorySlug);
        LocalRepository someRepository = new LocalRepository(defaultBranch);
        someRepository.configureRepos("localRepository", remoteRepositoryFolder);
        Git remoteGit = someRepository.originGit;
        Path localRepositoryFolder = someRepository.localRepoFile.toPath();
        Git localGit = someRepository.localGit;

        localVCLocalCITestService.testFetchThrowsException(localGit, student1Login, projectKey, repositorySlug, internalServerError);
        localVCLocalCITestService.testPushThrowsException(localGit, student1Login, projectKey, repositorySlug, internalServerError);

        // Cleanup
        localGit.close();
        FileUtils.deleteDirectory(localRepositoryFolder.toFile());
        remoteGit.close();
        FileUtils.deleteDirectory(remoteRepositoryFolder.toFile());
    }

    @Test
    void testFetchPush_offlineIDENotAllowed() {
        programmingExercise.setAllowOfflineIde(false);
        programmingExerciseRepository.save(programmingExercise);

        localVCLocalCITestService.testFetchThrowsException(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, forbidden);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, forbidden);

        programmingExercise.setAllowOfflineIde(true);
        programmingExerciseRepository.save(programmingExercise);
    }

    @Test
    void testFetchPush_assignmentRepository_student_noParticipation() throws GitAPIException, IOException, URISyntaxException {
        // Create a new repository, but don't create a participation for student2.
        String repositorySlug = projectKey1.toLowerCase() + "-" + student2Login;
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, repositorySlug);
        LocalRepository someRepository = new LocalRepository(defaultBranch);
        someRepository.configureRepos("localRepository", remoteRepositoryFolder);
        Git remoteGit = someRepository.originGit;
        Path localRepositoryFolder = someRepository.localRepoFile.toPath();
        Git localGit = someRepository.localGit;

        localVCLocalCITestService.testFetchThrowsException(localGit, student2Login, projectKey1, repositorySlug, internalServerError);
        localVCLocalCITestService.testPushThrowsException(localGit, student2Login, projectKey1, repositorySlug, internalServerError);

        // Cleanup
        localGit.close();
        FileUtils.deleteDirectory(localRepositoryFolder.toFile());
        remoteGit.close();
        FileUtils.deleteDirectory(remoteRepositoryFolder.toFile());
    }

    @Test
    void testFetchPush_templateRepository_teachingAssistant_noParticipation() {

    }

    @Test
    void testFetchPush_solutionRepository_teachingAssistant_noParticipation() {

    }

    @Test
    void testUserTriesToDeleteBranch() throws GitAPIException {
        // ":" prefix in the refspec means delete the branch in the remote repository.
        RefSpec refSpec = new RefSpec(":refs/heads/" + defaultBranch);
        String repositoryUrl = localVCLocalCITestService.constructLocalVCUrl(student1Login, projectKey1, assignmentRepositorySlug);
        PushResult pushResult = assignmentRepository.localGit.push().setRefSpecs(refSpec).setRemote(repositoryUrl).call().iterator().next();
        RemoteRefUpdate remoteRefUpdate = pushResult.getRemoteUpdates().iterator().next();
        assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
        assertThat(remoteRefUpdate.getMessage()).isEqualTo("You cannot delete a branch.");
    }

    @Test
    void testUserTriesToForcePush() throws Exception {
        String repositoryUrl = localVCLocalCITestService.constructLocalVCUrl(student1Login, projectKey1, assignmentRepositorySlug);

        // Create a second local repository, push a file from there, and then try to force push from the original local repository.
        Path tempDirectory = Files.createTempDirectory("tempDirectory");
        Git secondLocalGit = Git.cloneRepository().setURI(repositoryUrl).setDirectory(tempDirectory.toFile()).call();
        // secondLocalGit.remoteAdd().setName("origin").setUri(new URIish(String.valueOf(assignmentRepository.originRepoFile))).call();
        localVCLocalCITestService.commitFile(tempDirectory, programmingExercise.getPackageFolderName(), secondLocalGit);
        localVCLocalCITestService.testPushSuccessful(secondLocalGit, student1Login, projectKey1, assignmentRepositorySlug);

        localVCLocalCITestService.commitFile(assignmentRepository.localRepoFile.toPath(), programmingExercise.getPackageFolderName(), assignmentRepository.localGit,
                "second-test.txt");

        // Try to push normally, should fail because the remote already contains work that is not there locally.
        PushResult pushResultNormal = assignmentRepository.localGit.push().setRemote(repositoryUrl).call().iterator().next();
        RemoteRefUpdate remoteRefUpdateNormal = pushResultNormal.getRemoteUpdates().iterator().next();
        assertThat(remoteRefUpdateNormal.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD);

        // Force push from the original local repository.
        PushResult pushResultForce = assignmentRepository.localGit.push().setForce(true).setRemote(repositoryUrl).call().iterator().next();
        RemoteRefUpdate remoteRefUpdate = pushResultForce.getRemoteUpdates().iterator().next();
        assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
        assertThat(remoteRefUpdate.getMessage()).isEqualTo("You cannot force push.");

        // Cleanup
        secondLocalGit.close();
        FileUtils.deleteDirectory(tempDirectory.toFile());
    }
}
