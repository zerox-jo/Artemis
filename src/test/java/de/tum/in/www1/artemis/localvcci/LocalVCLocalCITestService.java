package de.tum.in.www1.artemis.localvcci;

import static de.tum.in.www1.artemis.util.ModelFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.util.GitUtilService;
import de.tum.in.www1.artemis.util.LocalRepository;

@Service
public class LocalVCLocalCITestService {

    @Autowired
    private ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Autowired
    private GitUtilService gitUtilService;

    // Cannot inject {local.server.port} here, because it is not available at the time this class is instantiated.
    private int port;

    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the commitHash for the assignment repository.
     *
     * @param mockDockerClient the mocked DockerClient.
     * @param commitHash       the commit hash to return.
     */
    public void mockCommitHash(DockerClient mockDockerClient, String commitHash) throws IOException {
        mockInputStreamReturnedFromContainer(mockDockerClient, "/repositories/assignment-repository/.git/refs/heads/[^/]+", Map.of("assignmentCommitHash", commitHash));
    }

    /**
     * Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
     *
     * @param dockerClient    the mocked DockerClient.
     * @param testResultsPath the path to the directory containing the test results in the resources folder.
     */
    public void mockTestResults(DockerClient dockerClient, Path testResultsPath) throws IOException {
        mockInputStreamReturnedFromContainer(dockerClient, "/repositories/test-repository/build/test-results/test", createMapFromTestResultsFolder(testResultsPath));
    }

    /**
     * Mocks the InputStream returned by dockerClient.copyArchiveFromContainerCmd(String containerId, String resource).exec()
     *
     * @param dockerClient         the DockerClient to be mocked.
     * @param resourceRegexPattern the regex pattern that the resource path must match. The resource path is the path of the file or directory inside the container.
     * @param dataToReturn         the data to return inside the InputStream in form of a map. Each entry of the map will be one TarArchiveEntry with the key denoting the
     *                                 tarArchiveEntry.getName() and the value being the content of the TarArchiveEntry. There can be up to two dataToReturn entries, in which case
     *                                 the first call to "copyArchiveFromContainerCmd().exec()" will return the first entry, and the second call will return the second entry.
     * @throws IOException if the InputStream cannot be created.
     */
    @SafeVarargs
    public final void mockInputStreamReturnedFromContainer(DockerClient dockerClient, String resourceRegexPattern, Map<String, String>... dataToReturn) throws IOException {
        // Mock dockerClient.copyArchiveFromContainerCmd(String containerId, String resource).exec()
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmd = mock(CopyArchiveFromContainerCmd.class);
        ArgumentMatcher<String> expectedPathMatcher = path -> path.matches(resourceRegexPattern);
        doReturn(copyArchiveFromContainerCmd).when(dockerClient).copyArchiveFromContainerCmd(anyString(), argThat(expectedPathMatcher));

        if (dataToReturn.length == 0) {
            throw new IllegalArgumentException("At least one dataToReturn entry must be provided.");
        }

        if (dataToReturn.length > 2) {
            throw new IllegalArgumentException("Only two dataToReturn entries are supported.");
        }

        if (dataToReturn.length == 1) {
            // If only one dataToReturn entry is provided, return it for every call to "copyArchiveFromContainerCmd().exec()"
            when(copyArchiveFromContainerCmd.exec()).thenReturn(createInputStreamForTarArchiveFromMap(dataToReturn[0]));
        }
        else {
            // If two dataToReturn entries are provided, return the first one for the first call to "copyArchiveFromContainerCmd().exec()" and the second one for the second call to
            // "copyArchiveFromContainerCmd().exec()"
            when(copyArchiveFromContainerCmd.exec()).thenReturn(createInputStreamForTarArchiveFromMap(dataToReturn[0]), createInputStreamForTarArchiveFromMap(dataToReturn[1]));
        }
    }

    /**
     * Create a BufferedInputStream from a map. Each entry of the map will be one TarArchiveEntry with the key denoting the tarArchiveEntry.getName() and the value being the
     * content.
     * The returned InputStream can be used to mock the InputStream returned by dockerClient.copyArchiveFromContainerCmd(String containerId, String resource).exec().
     *
     * @param dataMap the data to return inside the InputStream in form of a map.
     * @return the BufferedInputStream.
     * @throws IOException if any interaction with the TarArchiveOutputStream fails.
     */
    public BufferedInputStream createInputStreamForTarArchiveFromMap(Map<String, String> dataMap) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(byteArrayOutputStream);

        for (Map.Entry<String, String> entry : dataMap.entrySet()) {
            String filePath = entry.getKey();
            String content = entry.getValue();

            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

            TarArchiveEntry tarEntry = new TarArchiveEntry(filePath);
            tarEntry.setSize(contentBytes.length);
            tarArchiveOutputStream.putArchiveEntry(tarEntry);
            tarArchiveOutputStream.write(contentBytes);
            tarArchiveOutputStream.closeArchiveEntry();
        }

        tarArchiveOutputStream.close();

        return new BufferedInputStream(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));
    }

    /**
     * Create the standard test cases for a programming exercise and save them in the database.
     *
     * @param programmingExercise the programming exercise for which the test cases should be created.
     */
    public void addTestCases(ProgrammingExercise programmingExercise) {
        // Clean up existing test cases
        testCaseRepository.deleteAll(testCaseRepository.findByExerciseId(programmingExercise.getId()));

        List<ProgrammingExerciseTestCase> testCases = new ArrayList<>();
        testCases.add(new ProgrammingExerciseTestCase().testName("testClass[SortStrategy]").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testAttributes[Context]").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testAttributes[Policy]").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testClass[MergeSort]").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testClass[BubbleSort]").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testConstructors[Policy]").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testMethods[Context]").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testMethods[Policy]").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testMethods[SortStrategy]").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testMergeSort()").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testUseBubbleSortForSmallList()").weight(1.0).active(true).exercise(programmingExercise)
                .visibility(Visibility.ALWAYS).bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testUseMergeSortForBigList()").weight(1.0).active(true).exercise(programmingExercise)
                .visibility(Visibility.ALWAYS).bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testBubbleSort()").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));

        testCaseRepository.saveAll(testCases);

        List<ProgrammingExerciseTestCase> tests = new ArrayList<>(testCaseRepository.findByExerciseId(programmingExercise.getId()));
        assertThat(tests).as("test case is initialized").hasSize(13);
    }

    /**
     * Create a folder in the temporary directory with the project key as its name and another folder inside there that gets the name of the repository slug + ".git".
     * This is consistent with the repository folder structure used for the local VC system (though the repositories for the local VC system are not saved in the temporary
     * directory).
     *
     * @param projectKey     the project key of the repository.
     * @param repositorySlug the repository slug of the repository.
     * @return the path to the repository folder.
     */
    public Path createRepositoryFolderInTempDirectory(String projectKey, String repositorySlug) {
        String tempDir = System.getProperty("java.io.tmpdir");

        Path projectFolder = Paths.get(tempDir, projectKey);

        // Create the project folder if it does not exist.
        try {
            if (!Files.exists(projectFolder)) {
                Files.createDirectories(projectFolder);
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Create the repository folder.
        Path repositoryFolder = projectFolder.resolve(repositorySlug + ".git");
        try {
            Files.createDirectories(repositoryFolder);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        return repositoryFolder;
    }

    /**
     * Construct a repository URL that works with the local VC system.
     *
     * @param username       the username of the user that tries to access the repository using this URL.
     * @param projectKey     the project key of the repository.
     * @param repositorySlug the repository slug of the repository.
     * @return the URL to the repository.
     */
    public String constructLocalVCUrl(String username, String projectKey, String repositorySlug) {
        return constructLocalVCUrl(username, USER_PASSWORD, projectKey, repositorySlug);
    }

    private String constructLocalVCUrl(String username, String password, String projectKey, String repositorySlug) {
        return "http://" + username + (password.length() > 0 ? ":" : "") + password + "@localhost:" + port + "/git/" + projectKey.toUpperCase() + "/" + repositorySlug + ".git";
    }

    /**
     * Create a map from the files in a folder containing test results.
     * This map contains one entry for each file in the folder, the key being the file path and the value being the content of the file in case it is an XML file.
     * This map is used by localVCLocalCITestService.mockInputStreamReturnedFromContainer() to mock the InputStream returned by dockerClient.copyArchiveFromContainerCmd() and thus
     * mocks the retrieval of test results from the Docker container.
     *
     * @param testResultsPath Path to the folder containing the test results.
     * @return Map containing the file paths and the content of the files.
     */
    public Map<String, String> createMapFromTestResultsFolder(Path testResultsPath) throws IOException {
        Map<String, String> resultMap = new HashMap<>();
        String testResultsPathString = testResultsPath.toString();

        Files.walkFileTree(testResultsPath, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isDirectory()) {
                    String key = file.toString().replace(testResultsPathString, "test");
                    String value;
                    if (file.getFileName().toString().endsWith(".xml")) {
                        value = new String(Files.readAllBytes(file));
                    }
                    else {
                        value = "dummy-data";
                    }
                    resultMap.put(key, value);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return resultMap;
    }

    /**
     * Create a file in the local repository and commit it.
     *
     * @param localRepositoryFolder the path to the local repository.
     * @param packageFolderName     the name of the package folder.
     * @param localGit              the Git object for the local repository.
     * @return the commit hash.
     * @throws Exception if the file could not be created or committed.
     */
    public String commitFile(Path localRepositoryFolder, String packageFolderName, Git localGit) throws Exception {
        return commitFile(localRepositoryFolder, packageFolderName, localGit, "test.txt");
    }

    /**
     * Create a file in the local repository and commit it.
     *
     * @param localRepositoryFolder the path to the local repository.
     * @param packageFolderName     the name of the package folder.
     * @param localGit              the Git object for the local repository.
     * @param fileName              the name of the file to be created.
     * @return the commit hash.
     * @throws Exception if the file could not be created or committed.
     */
    public String commitFile(Path localRepositoryFolder, String packageFolderName, Git localGit, String fileName) throws Exception {
        Path testJsonFilePath = Path.of(localRepositoryFolder.toString(), "src", packageFolderName, fileName);
        gitUtilService.writeEmptyJsonFileToPath(testJsonFilePath);
        localGit.add().addFilepattern(".").call();
        RevCommit commit = localGit.commit().setMessage("Add " + fileName).call();
        return commit.getId().getName();
    }

    /**
     * Perform a fetch operation and fail if there was an exception.
     *
     * @param repositoryHandle the Git object for the repository.
     * @param username         the username of the user that tries to fetch from the repository.
     * @param projectKey       the project key of the repository.
     * @param repositorySlug   the repository slug of the repository.
     */
    public void testFetchSuccessful(Git repositoryHandle, String username, String projectKey, String repositorySlug) {
        testFetchSuccessful(repositoryHandle, username, USER_PASSWORD, projectKey, repositorySlug);
    }

    /**
     * Perform a fetch operation and fail if there was an exception.
     *
     * @param repositoryHandle the Git object for the repository.
     * @param username         the username of the user that tries to fetch from the repository.
     * @param password         the password of the user that tries to fetch from the repository.
     * @param projectKey       the project key of the repository.
     * @param repositorySlug   the repository slug of the repository.
     */
    public void testFetchSuccessful(Git repositoryHandle, String username, String password, String projectKey, String repositorySlug) {
        try {
            performFetch(repositoryHandle, username, password, projectKey, repositorySlug);
        }
        catch (GitAPIException e) {
            fail("Fetching was not successful: " + e.getMessage());
        }
    }

    /**
     * Perform a fetch operation and fail if there was no exception.
     *
     * @param repositoryHandle the Git object for the repository.
     * @param username         the username of the user that tries to fetch from the repository.
     * @param projectKey       the project key of the repository.
     * @param repositorySlug   the repository slug of the repository.
     * @param expectedMessage  the expected message of the exception.
     */
    public void testFetchThrowsException(Git repositoryHandle, String username, String projectKey, String repositorySlug, String expectedMessage) {
        testFetchThrowsException(repositoryHandle, username, USER_PASSWORD, projectKey, repositorySlug, expectedMessage);
    }

    /**
     * Perform a fetch operation and fail if there was no exception.
     *
     * @param repositoryHandle the Git object for the repository.
     * @param username         the username of the user that tries to fetch from the repository.
     * @param password         the password of the user that tries to fetch from the repository.
     * @param projectKey       the project key of the repository.
     * @param repositorySlug   the repository slug of the repository.
     * @param expectedMessage  the expected message of the exception.
     */
    public void testFetchThrowsException(Git repositoryHandle, String username, String password, String projectKey, String repositorySlug, String expectedMessage) {
        testFetchThrowsException(repositoryHandle, username, password, projectKey, repositorySlug, TransportException.class, expectedMessage);
    }

    /**
     * Perform a fetch operation and fail if there was no exception.
     *
     * @param repositoryHandle  the Git object for the repository.
     * @param username          the username of the user that tries to fetch from the repository.
     * @param password          the password of the user that tries to fetch from the repository.
     * @param projectKey        the project key of the repository.
     * @param repositorySlug    the repository slug of the repository.
     * @param expectedException the expected exception.
     * @param expectedMessage   the expected message of the exception.
     * @param <T>               the type of the expected exception.
     */
    public <T extends Exception> void testFetchThrowsException(Git repositoryHandle, String username, String password, String projectKey, String repositorySlug,
            Class<T> expectedException, String expectedMessage) {
        T exception = assertThrows(expectedException, () -> performFetch(repositoryHandle, username, password, projectKey, repositorySlug));
        assertThat(exception.getMessage()).contains(expectedMessage);
    }

    private void performFetch(Git repositoryHandle, String username, String password, String projectKey, String repositorySlug) throws GitAPIException {
        String repositoryUrl = constructLocalVCUrl(username, password, projectKey, repositorySlug);
        FetchCommand fetchCommand = repositoryHandle.fetch();
        // Set the remote URL.
        fetchCommand.setRemote(repositoryUrl);
        // Set the refspec to fetch all branches.
        fetchCommand.setRefSpecs(new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
        // Execute the fetch.
        fetchCommand.call();
    }

    /**
     * Perform a push operation and fail if there was an exception.
     *
     * @param repositoryHandle the Git object for the repository.
     * @param username         the username of the user that tries to push to the repository.
     * @param projectKey       the project key of the repository.
     * @param repositorySlug   the repository slug of the repository.
     */
    public void testPushSuccessful(Git repositoryHandle, String username, String projectKey, String repositorySlug) {
        try {
            performPush(repositoryHandle, username, USER_PASSWORD, projectKey, repositorySlug);
        }
        catch (GitAPIException e) {
            fail("Pushing was not successful: " + e.getMessage());
        }
    }

    /**
     * Assert that the latest submission has the correct commit hash and the correct result.
     *
     * @param participationId                 of the participation to check the latest submission for.
     * @param expectedCommitHash              the commit hash of the commit that triggered the creation of the submission and is thus expected to be seved in the submission.
     * @param expectedSuccessfulTestCaseCount the expected number or passed test cases.
     * @param buildFailed                     whether the build should have failed or not.
     */
    public void testLastestSubmission(Long participationId, String expectedCommitHash, int expectedSuccessfulTestCaseCount, boolean buildFailed) {
        ProgrammingSubmission programmingSubmission = programmingSubmissionRepository.findFirstByParticipationIdOrderByLegalSubmissionDateDesc(participationId).orElseThrow();
        assertThat(programmingSubmission.getCommitHash()).isEqualTo(expectedCommitHash);
        assertThat(programmingSubmission.isBuildFailed()).isEqualTo(buildFailed);
        Result result = programmingSubmission.getLatestResult();
        assertThat(result).isNotNull();
        int expectedTestCaseCount = buildFailed ? 0 : 13;
        assertThat(result.getTestCaseCount()).isEqualTo(expectedTestCaseCount);
        assertThat(result.getPassedTestCaseCount()).isEqualTo(expectedSuccessfulTestCaseCount);
    }

    /**
     * Perform a push operation and fail if there was no exception.
     *
     * @param repositoryHandle the Git object for the repository.
     * @param username         the username of the user that tries to push to the repository.
     * @param projectKey       the project key of the repository.
     * @param repositorySlug   the repository slug of the repository.
     * @param expectedMessage  the expected message of the exception.
     */
    public void testPushThrowsException(Git repositoryHandle, String username, String projectKey, String repositorySlug, String expectedMessage) {
        testPushThrowsException(repositoryHandle, username, USER_PASSWORD, projectKey, repositorySlug, expectedMessage);
    }

    /**
     * Perform a push operation and fail if there was no exception.
     *
     * @param repositoryHandle the Git object for the repository.
     * @param username         the username of the user that tries to push to the repository.
     * @param password         the password of the user that tries to push to the repository.
     * @param projectKey       the project key of the repository.
     * @param repositorySlug   the repository slug of the repository.
     * @param expectedMessage  the expected message of the exception.
     */
    public void testPushThrowsException(Git repositoryHandle, String username, String password, String projectKey, String repositorySlug, String expectedMessage) {
        TransportException exception = assertThrows(TransportException.class, () -> performPush(repositoryHandle, username, password, projectKey, repositorySlug));
        assertThat(exception.getMessage()).contains(expectedMessage);
    }

    private void performPush(Git repositoryHandle, String username, String password, String projectKey, String repositorySlug) throws GitAPIException {
        String repositoryUrl = constructLocalVCUrl(username, password, projectKey, repositorySlug);
        PushCommand pushCommand = repositoryHandle.push();
        // Set the remote URL.
        pushCommand.setRemote(repositoryUrl);
        // Execute the push.
        pushCommand.call();
    }

    /**
     * Close repository handles and delete temporary directories of a LocalRepository instance.
     *
     * @param repository to close and delete.
     */
    public void removeRepository(LocalRepository repository) {
        repository.localGit.close();
        repository.originGit.close();
        try {
            FileUtils.deleteDirectory(repository.localRepoFile);
            FileUtils.deleteDirectory(repository.originRepoFile);
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to delete repository folder.", e);
        }
    }
}
