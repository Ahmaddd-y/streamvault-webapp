package com.streamvault.db;

public class TestDB {
    public static void main(String[] args) {
        String hash = org.mindrot.jbcrypt.BCrypt.hashpw(
                "Password123!",
                org.mindrot.jbcrypt.BCrypt.gensalt(12)
        );

        System.out.println(hash);
        System.out.println(org.mindrot.jbcrypt.BCrypt.checkpw("Password123!", hash));
    }
}