package com.finanzapp.app.ui.family;

import com.finanzapp.app.data.model.Invitation;
import com.finanzapp.app.data.model.Member;

public class MemberListItem {
    public static final int TYPE_MEMBER = 0;
    public static final int TYPE_INVITATION = 1;
    public static final int TYPE_REQUEST = 2;

    private final int type;
    private final Member member;
    private final Invitation invitation;

    public MemberListItem(Member member) {
        this.type = TYPE_MEMBER;
        this.member = member;
        this.invitation = null;
    }

    public MemberListItem(Invitation invitation) {
        if ("code_request".equals(invitation.getType())) {
            this.type = TYPE_REQUEST;
        } else {
            this.type = TYPE_INVITATION;
        }
        this.member = null;
        this.invitation = invitation;
    }

    public int getType() { return type; }
    public Member getMember() { return member; }
    public Invitation getInvitation() { return invitation; }
}
