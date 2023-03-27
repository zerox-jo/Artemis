package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;
import static de.tum.in.www1.artemis.domain.notification.SingleUserNotificationFactory.createNotification;
import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsCommunicationChannel.*;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Posting;
import de.tum.in.www1.artemis.domain.notification.NotificationConstants;
import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.repository.SingleUserNotificationRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;

@Service
public class SingleUserNotificationService {

    private final SingleUserNotificationRepository singleUserNotificationRepository;

    private final UserRepository userRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    private final GeneralInstantNotificationService notificationService;

    private final NotificationSettingsService notificationSettingsService;

    private final StudentParticipationRepository studentParticipationRepository;

    public SingleUserNotificationService(SingleUserNotificationRepository singleUserNotificationRepository, UserRepository userRepository,
            SimpMessageSendingOperations messagingTemplate, GeneralInstantNotificationService notificationService, NotificationSettingsService notificationSettingsService,
            StudentParticipationRepository studentParticipationRepository) {
        this.singleUserNotificationRepository = singleUserNotificationRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.notificationService = notificationService;
        this.notificationSettingsService = notificationSettingsService;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * Auxiliary method to call the correct factory method and start the process to save & sent the notification
     *
     * @param notificationSubject     is the subject of the notification (e.g. exercise, attachment)
     * @param notificationType        is the discriminator for the factory
     * @param typeSpecificInformation is based on the current use case (e.g. POST -> course, Exercise -> user)
     */
    private void notifyRecipientWithNotificationType(Object notificationSubject, NotificationType notificationType, Object typeSpecificInformation, User author) {
        var singleUserNotification = switch (notificationType) {
            // Post Types
            case NEW_REPLY_FOR_EXERCISE_POST, NEW_REPLY_FOR_LECTURE_POST, NEW_REPLY_FOR_COURSE_POST -> createNotification((Post) ((List<Posting>) notificationSubject).get(0),
                    (AnswerPost) ((List<Posting>) notificationSubject).get(1), notificationType, (Course) typeSpecificInformation);
            // Exercise related
            case EXERCISE_SUBMISSION_ASSESSED, FILE_SUBMISSION_SUCCESSFUL -> createNotification((Exercise) notificationSubject, notificationType, (User) typeSpecificInformation);
            // Plagiarism related
            case NEW_PLAGIARISM_CASE_STUDENT, PLAGIARISM_CASE_VERDICT_STUDENT -> createNotification((PlagiarismCase) notificationSubject, notificationType,
                    (User) typeSpecificInformation, author);
            // Tutorial Group related
            case TUTORIAL_GROUP_REGISTRATION_STUDENT, TUTORIAL_GROUP_DEREGISTRATION_STUDENT, TUTORIAL_GROUP_REGISTRATION_TUTOR, TUTORIAL_GROUP_DEREGISTRATION_TUTOR, TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR, TUTORIAL_GROUP_ASSIGNED, TUTORIAL_GROUP_UNASSIGNED -> createNotification(
                    ((TutorialGroupNotificationSubject) notificationSubject).tutorialGroup, notificationType, ((TutorialGroupNotificationSubject) notificationSubject).users,
                    ((TutorialGroupNotificationSubject) notificationSubject).responsibleUser);
            default -> throw new UnsupportedOperationException("Can not create notification for type : " + notificationType);
        };
        saveAndSend(singleUserNotification, notificationSubject);
    }

    /**
     * Notify all users with available assessments about the finished assessment for an exercise submission.
     * This is an auxiliary method that finds all relevant users and initiates the process for sending SingleUserNotifications and emails
     *
     * @param exercise which assessmentDueDate is the trigger for the notification process
     */
    public void notifyUsersAboutAssessedExerciseSubmission(Exercise exercise) {
        // This process can not be replaces via a GroupNotification (can only notify ALL students of the course)
        // because we want to notify only the students that have a valid assessed submission.

        // Find student participations with eager legal submissions and latest results that have a completion date
        Set<StudentParticipation> filteredStudentParticipations = Set
                .copyOf(studentParticipationRepository.findByExerciseIdAndTestRunWithEagerLegalSubmissionsAndLatestResultWithCompletionDate(exercise.getId(), false));

        // Load and assign all studentParticipations with results (this information is needed for the emails later)
        exercise.setStudentParticipations(filteredStudentParticipations);

        // Extract all users that should be notified from the previously loaded student participations
        Set<User> relevantStudents = filteredStudentParticipations.stream().map(participation -> participation.getStudent().orElseThrow()).collect(Collectors.toSet());

        // notify all relevant users
        relevantStudents.forEach(student -> notifyUserAboutAssessedExerciseSubmission(exercise, student));
    }

    /**
     * Notify author of a post for an exercise that there is a new reply.
     *
     * @param post       that is replied
     * @param answerPost that is replied with
     * @param course     that the post belongs to
     */
    public void notifyUserAboutNewReplyForExercise(Post post, AnswerPost answerPost, Course course) {
        notifyRecipientWithNotificationType(Arrays.asList(post, answerPost), NEW_REPLY_FOR_EXERCISE_POST, course, post.getAuthor());
    }

    /**
     * Notify author of a post for a lecture that there is a new reply.
     *
     * @param post       that is replied
     * @param answerPost that is replied with
     * @param course     that the post belongs to
     */
    public void notifyUserAboutNewReplyForLecture(Post post, AnswerPost answerPost, Course course) {
        notifyRecipientWithNotificationType(Arrays.asList(post, answerPost), NEW_REPLY_FOR_LECTURE_POST, course, post.getAuthor());
    }

    /**
     * Notify author of a course-wide that there is a new reply.
     * Also creates and sends an email.
     *
     * @param post       that is replied
     * @param answerPost that is replied with
     * @param course     that the post belongs to
     */
    public void notifyUserAboutNewReplyForCoursePost(Post post, AnswerPost answerPost, Course course) {
        notifyRecipientWithNotificationType(Arrays.asList(post, answerPost), NEW_REPLY_FOR_COURSE_POST, course, post.getAuthor());
    }

    /**
     * Notify student about the finished assessment for an exercise submission.
     * Also creates and sends an email.
     * <p>
     * private because it is called by other methods that check e.g. if the time or results are correct
     *
     * @param exercise  that was assessed
     * @param recipient who should be notified
     */
    private void notifyUserAboutAssessedExerciseSubmission(Exercise exercise, User recipient) {
        notifyRecipientWithNotificationType(exercise, EXERCISE_SUBMISSION_ASSESSED, recipient, null);
    }

    /**
     * Checks if a new assessed-exercise-submission notification has to be created now
     *
     * @param exercise  which the submission is based on
     * @param recipient of the notification (i.e. the student)
     * @param result    containing information needed for the email
     */
    public void checkNotificationForAssessmentExerciseSubmission(Exercise exercise, User recipient, Result result) {
        // only send the notification now if no assessment due date was set or if it is in the past
        if (exercise.isCourseExercise() && (exercise.getAssessmentDueDate() == null || exercise.getAssessmentDueDate().isBefore(ZonedDateTime.now()))) {
            saturateExerciseWithResultAndStudentParticipationForGivenUserForEmail(exercise, recipient, result);
            notifyUserAboutAssessedExerciseSubmission(exercise, recipient);
        }
        // no scheduling needed because it is already part of updating/creating exercises
    }

    /**
     * Auxiliary method needed to create an email based on assessed exercises.
     * We saturate the wanted result information (e.g. score) in the exercise
     * This method is only called in those cases where no assessmentDueDate is set, i.e. individual/dynamic processes.
     *
     * @param exercise  that should contain information that is needed for emails
     * @param recipient who should be notified
     * @param result    that should be loaded as part of the exercise
     * @return the input exercise with information about a result
     */
    public Exercise saturateExerciseWithResultAndStudentParticipationForGivenUserForEmail(Exercise exercise, User recipient, Result result) {
        StudentParticipation studentParticipationForEmail = new StudentParticipation();
        studentParticipationForEmail.setResults(Set.of(result));
        studentParticipationForEmail.setParticipant(recipient);
        exercise.setStudentParticipations(Set.of(studentParticipationForEmail));
        return exercise;
    }

    /**
     * Notify student about successful submission of file upload exercise.
     * Also creates and sends an email.
     *
     * @param exercise  that was submitted
     * @param recipient that should be notified
     */
    public void notifyUserAboutSuccessfulFileUploadSubmission(FileUploadExercise exercise, User recipient) {
        notifyRecipientWithNotificationType(exercise, FILE_SUBMISSION_SUCCESSFUL, recipient, null);
    }

    /**
     * Notify student about possible plagiarism case.
     *
     * @param plagiarismCase that hold the major information for the plagiarism case
     * @param student        who should be notified
     */
    public void notifyUserAboutNewPlagiarismCase(PlagiarismCase plagiarismCase, User student) {
        notifyRecipientWithNotificationType(plagiarismCase, NEW_PLAGIARISM_CASE_STUDENT, student, userRepository.getUser());
    }

    /**
     * Notify student about plagiarism case verdict.
     *
     * @param plagiarismCase that hold the major information for the plagiarism case
     * @param student        who should be notified
     */
    public void notifyUserAboutPlagiarismCaseVerdict(PlagiarismCase plagiarismCase, User student) {
        notifyRecipientWithNotificationType(plagiarismCase, PLAGIARISM_CASE_VERDICT_STUDENT, student, userRepository.getUser());
    }

    /**
     * Record to store tutorial group, users and responsible user in one notification subject.
     */
    public record TutorialGroupNotificationSubject(TutorialGroup tutorialGroup, Set<User> users, User responsibleUser) {
    }

    /**
     * Notify a student that he or she has been registered for a tutorial group.
     *
     * @param tutorialGroup   the tutorial group the student has been registered for
     * @param student         the student that has been registered for the tutorial group
     * @param responsibleUser the user that has registered the student for the tutorial group
     */
    public void notifyStudentAboutRegistrationToTutorialGroup(TutorialGroup tutorialGroup, User student, User responsibleUser) {
        notifyRecipientWithNotificationType(new TutorialGroupNotificationSubject(tutorialGroup, Set.of(student), responsibleUser), TUTORIAL_GROUP_REGISTRATION_STUDENT, null, null);
    }

    /**
     * Notify a student that he or she has been deregistered from a tutorial group.
     *
     * @param tutorialGroup   the tutorial group the student has been deregistered from
     * @param student         the student that has been deregistered from the tutorial group
     * @param responsibleUser the user that has deregistered the student from the tutorial group
     */
    public void notifyStudentAboutDeregistrationFromTutorialGroup(TutorialGroup tutorialGroup, User student, User responsibleUser) {
        notifyRecipientWithNotificationType(new TutorialGroupNotificationSubject(tutorialGroup, Set.of(student), responsibleUser), TUTORIAL_GROUP_DEREGISTRATION_STUDENT, null,
                null);
    }

    /**
     * Notify a tutor of tutorial group that multiple students have been registered for the tutorial group he or she is responsible for.
     *
     * @param tutorialGroup   the tutorial group the students have been registered for (containing the tutor that should be notified)
     * @param students        the students that have been registered for the tutorial group
     * @param responsibleUser the user that has registered the student for the tutorial group
     */
    public void notifyTutorAboutMultipleRegistrationsToTutorialGroup(TutorialGroup tutorialGroup, Set<User> students, User responsibleUser) {
        notifyRecipientWithNotificationType(new TutorialGroupNotificationSubject(tutorialGroup, students, responsibleUser), TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR, null, null);
    }

    /**
     * Notify a tutor of a tutorial group that a student has been registered for the tutorial group he or she is responsible for.
     *
     * @param tutorialGroup   the tutorial group the student has registered for (containing the tutor that should be notified)
     * @param student         the student that has been registered for the tutorial group
     * @param responsibleUser the user that has registered the student for the tutorial group
     */
    public void notifyTutorAboutRegistrationToTutorialGroup(TutorialGroup tutorialGroup, User student, User responsibleUser) {
        notifyRecipientWithNotificationType(new TutorialGroupNotificationSubject(tutorialGroup, Set.of(student), responsibleUser), TUTORIAL_GROUP_REGISTRATION_TUTOR, null, null);
    }

    /**
     * Notify a tutor of a tutorial group that a student has been deregistered from the tutorial group he or she is responsible for.
     *
     * @param tutorialGroup   the tutorial group the student has been deregistered from (containing the tutor that should be notified)
     * @param student         the student that has been deregistered from the tutorial group
     * @param responsibleUser the user that has deregistered the student from the tutorial group
     */
    public void notifyTutorAboutDeregistrationFromTutorialGroup(TutorialGroup tutorialGroup, User student, User responsibleUser) {
        notifyRecipientWithNotificationType(new TutorialGroupNotificationSubject(tutorialGroup, Set.of(student), responsibleUser), TUTORIAL_GROUP_DEREGISTRATION_TUTOR, null, null);
    }

    /**
     * Notify a tutor that he or she has been assigned to lead a tutorial group.
     *
     * @param tutorialGroup   the tutorial group the tutor has been assigned to lead
     * @param tutorToContact  the tutor that has been assigned to lead the tutorial group
     * @param responsibleUser the user that has assigned the tutor to lead the tutorial group
     */
    public void notifyTutorAboutAssignmentToTutorialGroup(TutorialGroup tutorialGroup, User tutorToContact, User responsibleUser) {
        notifyRecipientWithNotificationType(new TutorialGroupNotificationSubject(tutorialGroup, Set.of(tutorToContact), responsibleUser), TUTORIAL_GROUP_ASSIGNED, null, null);
    }

    /**
     * Notify a tutor that he or she has been unassigned from the leadership of a tutorial group.
     *
     * @param tutorialGroup   the tutorial group the tutor has been unassigned from
     * @param tutorToContact  the tutor that has been unassigned
     * @param responsibleUser the user that has unassigned the tutor
     */
    public void notifyTutorAboutUnassignmentFromTutorialGroup(TutorialGroup tutorialGroup, User tutorToContact, User responsibleUser) {
        notifyRecipientWithNotificationType(new TutorialGroupNotificationSubject(tutorialGroup, Set.of(tutorToContact), responsibleUser), TUTORIAL_GROUP_UNASSIGNED, null, null);
    }

    /**
     * Saves the given notification in database and sends it to the client via websocket.
     * Also creates and sends an instant notification.
     *
     * @param notification        that should be saved and sent
     * @param notificationSubject which information will be extracted to create the email
     */
    private void saveAndSend(SingleUserNotification notification, Object notificationSubject) {
        singleUserNotificationRepository.save(notification);
        // we only want to notify one individual user therefore we can check the settings and filter preemptively
        boolean isWebappNotificationAllowed = notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification,
                notification.getRecipient(), WEBAPP);
        if (isWebappNotificationAllowed) {
            messagingTemplate.convertAndSend(notification.getTopic(), notification);
        }

        prepareSingleUserInstantNotification(notification, notificationSubject);
    }

    /**
     * Checks if an instant notification should be created based on the provided notification, user, notification settings and type for SingleUserNotifications
     * If the checks are successful creates and sends a corresponding instant notification
     *
     * @param notification        that should be checked
     * @param notificationSubject which information will be extracted to create the email
     */
    private void prepareSingleUserInstantNotification(SingleUserNotification notification, Object notificationSubject) {
        NotificationType type = NotificationConstants.findCorrespondingNotificationType(notification.getTitle());
        // checks if this notification type has email support
        if (notificationSettingsService.checkNotificationTypeForInstantNotificationSupport(type)) {
            notificationService.sendNotification(notification, notification.getRecipient(), notificationSubject);
        }
    }
}
