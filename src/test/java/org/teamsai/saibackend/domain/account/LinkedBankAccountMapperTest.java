package org.teamsai.saibackend.domain.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.account.dto.LinkedBankAccountDTO;
import org.teamsai.saibackend.domain.account.dto.type.ConnectionStatus;
import org.teamsai.saibackend.domain.account.mapper.LinkedBankAccountMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
@DisplayName("LinkedBankAccountMapper 통합 테스트")
class LinkedBankAccountMapperTest {

    @Autowired
    private LinkedBankAccountMapper linkedBankAccountMapper;

    private LinkedBankAccountDTO createDto(Long userId, Long accountId, String bankCode, String accountNumber) {
        LocalDateTime now = LocalDateTime.now();

        return LinkedBankAccountDTO.builder()
                .userId(userId)
                .accountId(accountId)
                .bankCode(bankCode)
                .accountNumber(accountNumber)
                .accountAlias("테스트계좌")
                .accountHolderName("홍길동")
                .balance(BigDecimal.valueOf(10000L))
                .connectionStatus(ConnectionStatus.AVAILABLE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    @Transactional
    @DisplayName("계좌를 저장하면 DTO에 생성된 linkedAccountId가 채워진다")
    void insertOne_assignsGeneratedKeyToDto() {
        LinkedBankAccountDTO account = createDto(1L, 101L, "088", "1111111111");

        linkedBankAccountMapper.insertOne(account);

        assertThat(account.getLinkedAccountId()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("여러 계좌를 각각 저장하면 서로 다른 linkedAccountId가 채워진다")
    void insertOne_multipleAccounts_assignsDistinctKeys() {
        LinkedBankAccountDTO account1 = createDto(1L, 101L, "088", "1111111111");
        LinkedBankAccountDTO account2 = createDto(1L, 102L, "004", "2222222222");

        linkedBankAccountMapper.insertOne(account1);
        linkedBankAccountMapper.insertOne(account2);

        assertThat(account1.getLinkedAccountId()).isNotNull();
        assertThat(account2.getLinkedAccountId()).isNotNull();
        assertThat(account1.getLinkedAccountId()).isNotEqualTo(account2.getLinkedAccountId());
    }

    @Test
    @Transactional
    @DisplayName("저장 후 사용자 ID로 조회하면 저장했던 계좌 정보를 그대로 가져온다")
    void insertOne_thenSelectByUserId_returnsSavedAccounts() {
        Long userId = 2L;
        LinkedBankAccountDTO account1 = createDto(userId, 201L, "088", "3333333333");
        LinkedBankAccountDTO account2 = createDto(userId, 202L, "020", "4444444444");

        linkedBankAccountMapper.insertOne(account1);
        linkedBankAccountMapper.insertOne(account2);

        List<LinkedBankAccountDTO> result =
                linkedBankAccountMapper.selectLinkedAccountsByUserId(userId);

        // 다른 데이터가 섞여 있어도, 내가 방금 넣은 것들이 포함되어 있는지만 확인
        assertThat(result)
                .extracting(LinkedBankAccountDTO::getAccountId)
                .contains(201L, 202L);

        assertThat(result)
                .filteredOn(dto -> dto.getAccountId().equals(201L) || dto.getAccountId().equals(202L))
                .extracting(LinkedBankAccountDTO::getLinkedAccountId)
                .doesNotContainNull();
    }
}