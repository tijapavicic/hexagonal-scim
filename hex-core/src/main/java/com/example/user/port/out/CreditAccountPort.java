package com.example.user.port.out;

import java.math.BigDecimal;

public interface CreditAccountPort {

    void credit(Long accountId, BigDecimal amount);
}

