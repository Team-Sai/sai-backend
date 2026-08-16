package org.teamsai.saibackend.domain.link.event;

public record PreviousUserKeyRevokedEvent(Long userId, String previousUserKey) {
}