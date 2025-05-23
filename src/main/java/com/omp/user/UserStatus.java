package com.omp.user;

public enum UserStatus {
    GENERAL, BAN;

    public boolean isBan() {
        return this.equals(BAN);
    }
}
