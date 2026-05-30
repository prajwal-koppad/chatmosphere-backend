package com.chatmosphere.backend.controller;

import com.chatmosphere.backend.dto.ContactDTO;
import com.chatmosphere.backend.service.ContactService;
import com.chatmosphere.backend.vo.AddContactRequestVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    @PostMapping
    public ResponseEntity<ContactDTO> addContact(@Valid @RequestBody AddContactRequestVO addContactRequest, Principal principal) {
        ContactDTO savedContact = contactService.addContact(principal.getName(), addContactRequest);
        return new ResponseEntity<>(savedContact, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<ContactDTO>> getContacts(Principal principal) {
        List<ContactDTO> contacts = contactService.getContacts(principal.getName());
        return ResponseEntity.ok(contacts);
    }
}
