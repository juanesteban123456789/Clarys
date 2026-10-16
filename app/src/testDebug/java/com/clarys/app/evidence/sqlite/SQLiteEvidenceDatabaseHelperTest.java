package com.clarys.app.evidence.sqlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class SQLiteEvidenceDatabaseHelperTest {

    @Test
    public void hashPasswordCreatesStableSha256WithoutPlainText() {
        String firstHash = SQLiteEvidenceDatabaseHelper.hashPassword("Sena1234*");
        String secondHash = SQLiteEvidenceDatabaseHelper.hashPassword("Sena1234*");

        assertEquals(64, firstHash.length());
        assertEquals(firstHash, secondHash);
        assertNotEquals("Sena1234*", firstHash);
    }
}
