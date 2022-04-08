package de.tum.in.www1.artemis.web.rest.metis;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.metis.Conversation;
import de.tum.in.www1.artemis.service.metis.ConversationService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing Conversation.
 */
@RestController
@RequestMapping("/api/courses")
public class ConversationResource {

    private final ConversationService conversationService;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    public ConversationResource(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * GET conversations : get all conversations for user within course by courseId
     *
     * @param courseId the courseId which the searched conversations belong to
     * @return the ResponseEntity with status 200 (OK) and with body
     */
    @GetMapping("/{courseId}/conversations")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<Conversation>> getConversationsOfUser(@PathVariable Long courseId) {
        List<Conversation> conversations = conversationService.getConversationsOfUser(courseId);
        return new ResponseEntity<>(conversations, null, HttpStatus.OK);
    }

    /**
     * POST /courses/{courseId}/conversations : Create a new conversatipn
     *
     * @param courseId        course to associate the new conversation
     * @param conversation    conversation to create
     * @return ResponseEntity with status 201 (Created) containing the created conversation in the response body,
     * or with status 400 (Bad Request) if the checks on user or course validity fail
     */
    @PostMapping("/{courseId}/conversations")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Conversation> createConversation(@PathVariable Long courseId, @Valid @RequestBody Conversation conversation) throws URISyntaxException {
        Conversation createdConversation = conversationService.createConversation(courseId, conversation);
        return ResponseEntity.created(new URI("/api/conversations/" + createdConversation.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, conversationService.getEntityName(), createdConversation.getId().toString()))
                .body(createdConversation);
    }
}