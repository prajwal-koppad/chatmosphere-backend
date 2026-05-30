package com.chatmosphere.backend.service;

import com.chatmosphere.backend.dto.ContactDTO;
import com.chatmosphere.backend.entity.Contact;
import com.chatmosphere.backend.entity.User;
import com.chatmosphere.backend.repository.ContactRepository;
import com.chatmosphere.backend.repository.UserRepository;
import com.chatmosphere.backend.vo.AddContactRequestVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;
    private final UserRepository userRepository;

    // =========================================================================
    // Core Business Methods
    // =========================================================================

    /**
     * Adds a new contact by verifying matching registered user accounts.
     */
    public ContactDTO addContact(String ownerUsername, AddContactRequestVO request) {
        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner user not found"));

        User contactUser = userRepository.findByMobileNumber(request.getMobileNumber())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, 
                        "No registered account found with mobile number " + request.getMobileNumber()
                ));

        validateContactAddition(ownerUsername, contactUser.getUsername(), request.getMobileNumber());

        Contact contact = new Contact();
        contact.setUser(owner);
        contact.setContactUser(contactUser);
        contact.setContactName(request.getContactName());
        contact.setCreatedBy(ownerUsername);
        contact.setCreateDate(LocalDateTime.now(ZoneOffset.UTC));

        Contact savedContact = contactRepository.save(contact);
        return mapToContactDTO(savedContact);
    }

    /**
     * Retrieves the contact list for a specific user.
     */
    public List<ContactDTO> getContacts(String username) {
        List<Contact> contacts = contactRepository.findByUserUsername(username);
        return contacts.stream()
                .map(this::mapToContactDTO)
                .toList();
    }

    // =========================================================================
    // Helpers and Validations
    // =========================================================================

    private void validateContactAddition(String ownerUsername, String contactUsername, String mobileNumber) {
        if (ownerUsername.equals(contactUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot add yourself to your contact list");
        }

        boolean exists = contactRepository.existsByUserUsernameAndContactUserMobileNumber(ownerUsername, mobileNumber);
        if (exists) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact already exists in your contact list");
        }
    }

    private ContactDTO mapToContactDTO(Contact contact) {
        if (contact == null) return null;

        ContactDTO dto = new ContactDTO();
        dto.setId(contact.getId());
        dto.setContactUsername(contact.getContactUser().getUsername());
        dto.setDefaultDisplayName(contact.getContactUser().getDisplayName());
        dto.setSavedContactName(contact.getContactName());
        dto.setMobileNumber(contact.getContactUser().getMobileNumber());
        dto.setAvatarUrl(contact.getContactUser().getAvatarUrl());
        return dto;
    }
}
