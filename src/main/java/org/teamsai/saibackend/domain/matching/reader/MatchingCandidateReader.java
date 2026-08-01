package org.teamsai.saibackend.domain.matching.reader;

import org.teamsai.saibackend.domain.matching.model.MatchingCandidate;

import java.util.List;

public interface MatchingCandidateReader {
    List<MatchingCandidate> readCandidates();
}
