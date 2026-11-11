package com.mendelev.mpos

import com.mendelev.mpos.data.local.*
import org.junit.Assert.*
import org.junit.Test

class ManagementContactsCodecTest {
    @Test fun ownerAndSeveralAdministratorsRoundTripIndependently() {
        val contacts=ManagementContacts(7,ManagementContact("owner","+375291234567","owner@example.com","123456"),
            listOf(ManagementContact("a","","+admin@example.com","-123"),ManagementContact("b","+375331234567")))
        assertEquals(contacts,ManagementContactsCodec.decode(ManagementContactsCodec.encode(contacts),7))
        assertEquals(ManagementContacts(),ManagementContactsCodec.decode(ManagementContactsCodec.encode(ManagementContacts()),0))
    }
    @Test fun duplicateIdsAndInvalidContactFieldsAreRejected() {
        listOf(
            ManagementContacts(administrators=listOf(ManagementContact("owner"))),
            ManagementContacts(administrators=listOf(ManagementContact("a"),ManagementContact("a"))),
            ManagementContacts(owner=ManagementContact("other")),
            ManagementContacts(owner=ManagementContact("owner",email="broken")),
            ManagementContacts(owner=ManagementContact("owner",telegramId="user_name"))
        ).forEach {assertTrue(runCatching {ManagementContactsCodec.encode(it)}.isFailure)}
    }
}
