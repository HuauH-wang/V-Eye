from __future__ import annotations

import uuid
from datetime import date, datetime, timezone
from sqlalchemy import Date

from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Integer, Text, UniqueConstraint
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship

from .time_utils import utc_now


class Base(DeclarativeBase):
    pass


class User(Base):
    __tablename__ = "users"
    __table_args__ = (UniqueConstraint("username", name="uq_users_username"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    username: Mapped[str] = mapped_column(Text, nullable=False)
    email: Mapped[str | None] = mapped_column(Text, nullable=True)
    password_hash: Mapped[str] = mapped_column(Text, nullable=False)
    display_name: Mapped[str] = mapped_column(Text, nullable=False, default="")
    avatar_path: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=datetime.utcnow
    )

    identify_records: Mapped[list["IdentifyRecord"]] = relationship(back_populates="user")
    team_memberships: Mapped[list["TeamMember"]] = relationship(back_populates="user")
    sent_mails: Mapped[list["UserMail"]] = relationship(
        back_populates="sender",
        foreign_keys="UserMail.sender_id",
    )
    received_mails: Mapped[list["UserMail"]] = relationship(
        back_populates="recipient",
        foreign_keys="UserMail.recipient_id",
    )


class Team(Base):
    __tablename__ = "teams"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name: Mapped[str] = mapped_column(Text, nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=False, default="")
    owner_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    avatar_path: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=datetime.utcnow
    )

    owner: Mapped[User] = relationship(foreign_keys=[owner_id])
    members: Mapped[list["TeamMember"]] = relationship(back_populates="team", cascade="all, delete-orphan")
    invites: Mapped[list["TeamInvite"]] = relationship(back_populates="team", cascade="all, delete-orphan")


class TeamMember(Base):
    __tablename__ = "team_members"
    __table_args__ = (UniqueConstraint("team_id", "user_id", name="uq_team_members_team_user"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    team_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("teams.id"), nullable=False, index=True)
    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False, index=True)
    role: Mapped[str] = mapped_column(Text, nullable=False, default="member")
    joined_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=datetime.utcnow
    )

    team: Mapped[Team] = relationship(back_populates="members")
    user: Mapped[User] = relationship(back_populates="team_memberships")


class TeamInvite(Base):
    __tablename__ = "team_invites"
    __table_args__ = (UniqueConstraint("team_id", "invitee_id", name="uq_team_invites_team_invitee"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    team_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("teams.id"), nullable=False, index=True)
    inviter_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    invitee_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False, index=True)
    status: Mapped[str] = mapped_column(Text, nullable=False, default="pending")
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=datetime.utcnow
    )

    team: Mapped[Team] = relationship(back_populates="invites")
    inviter: Mapped[User] = relationship(foreign_keys=[inviter_id])
    invitee: Mapped[User] = relationship(foreign_keys=[invitee_id])


class UserMail(Base):
    __tablename__ = "user_mails"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    recipient_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False, index=True)
    sender_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)
    mail_type: Mapped[str] = mapped_column(Text, nullable=False)
    title: Mapped[str] = mapped_column(Text, nullable=False)
    body: Mapped[str] = mapped_column(Text, nullable=False, default="")
    payload: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
    is_read: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    action_status: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=datetime.utcnow
    )

    recipient: Mapped[User] = relationship(back_populates="received_mails", foreign_keys=[recipient_id])
    sender: Mapped[User | None] = relationship(back_populates="sent_mails", foreign_keys=[sender_id])


class IdentifyRecord(Base):
    __tablename__ = "identify_records"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id"), nullable=True, index=True
    )
    device_id: Mapped[str | None] = mapped_column(Text, nullable=True)
    scene: Mapped[str] = mapped_column(Text, nullable=False)
    image_path: Mapped[str] = mapped_column(Text, nullable=False)
    image_original_path: Mapped[str | None] = mapped_column(Text, nullable=True)
    result_json: Mapped[dict] = mapped_column(JSONB, nullable=False)
    risk_level: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    label_main: Mapped[str] = mapped_column(Text, nullable=False, default="")
    server_ts: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=datetime.utcnow)
    gps_lat: Mapped[float | None] = mapped_column(Float, nullable=True)
    gps_lng: Mapped[float | None] = mapped_column(Float, nullable=True)
    accuracy_m: Mapped[float | None] = mapped_column(Float, nullable=True)
    sensor_json: Mapped[dict | None] = mapped_column(JSONB, nullable=True)

    user: Mapped[User | None] = relationship(back_populates="identify_records")


class SosEvent(Base):
    __tablename__ = "sos_events"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id"), nullable=True, index=True
    )
    device_id: Mapped[str] = mapped_column(Text, nullable=False)
    event_type: Mapped[str] = mapped_column(Text, nullable=False)
    gps_lat: Mapped[float | None] = mapped_column(Float, nullable=True)
    gps_lng: Mapped[float | None] = mapped_column(Float, nullable=True)
    accuracy_m: Mapped[float | None] = mapped_column(Float, nullable=True)
    client_ts: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    server_ts: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=datetime.utcnow)

    user: Mapped[User | None] = relationship(foreign_keys=[user_id])


class MemberLocation(Base):
    __tablename__ = "member_locations"

    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id"), primary_key=True
    )
    team_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("teams.id"), nullable=True, index=True
    )
    gps_lat: Mapped[float] = mapped_column(Float, nullable=False)
    gps_lng: Mapped[float] = mapped_column(Float, nullable=False)
    accuracy_m: Mapped[float | None] = mapped_column(Float, nullable=True)
    client_ts: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    server_ts: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=datetime.utcnow
    )

    user: Mapped[User] = relationship(foreign_keys=[user_id])
    team: Mapped[Team | None] = relationship(foreign_keys=[team_id])


class MemberTrackPoint(Base):
    __tablename__ = "member_track_points"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id"), nullable=False, index=True
    )
    team_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("teams.id"), nullable=True, index=True
    )
    source: Mapped[str] = mapped_column(Text, nullable=False, default="sensor")
    identify_record_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("identify_records.id"), nullable=True, index=True
    )
    gps_lat: Mapped[float] = mapped_column(Float, nullable=False)
    gps_lng: Mapped[float] = mapped_column(Float, nullable=False)
    gps_speed: Mapped[float | None] = mapped_column(Float, nullable=True)
    gps_heading: Mapped[float | None] = mapped_column(Float, nullable=True)
    gps_altitude: Mapped[float | None] = mapped_column(Float, nullable=True)
    accuracy_m: Mapped[float | None] = mapped_column(Float, nullable=True)
    accel_x: Mapped[float | None] = mapped_column(Float, nullable=True)
    accel_y: Mapped[float | None] = mapped_column(Float, nullable=True)
    accel_z: Mapped[float | None] = mapped_column(Float, nullable=True)
    gyro_x: Mapped[float | None] = mapped_column(Float, nullable=True)
    gyro_y: Mapped[float | None] = mapped_column(Float, nullable=True)
    gyro_z: Mapped[float | None] = mapped_column(Float, nullable=True)
    mag_x: Mapped[float | None] = mapped_column(Float, nullable=True)
    mag_y: Mapped[float | None] = mapped_column(Float, nullable=True)
    mag_z: Mapped[float | None] = mapped_column(Float, nullable=True)
    pitch: Mapped[float | None] = mapped_column(Float, nullable=True)
    roll: Mapped[float | None] = mapped_column(Float, nullable=True)
    imu_heading: Mapped[float | None] = mapped_column(Float, nullable=True)
    baro_altitude: Mapped[float | None] = mapped_column(Float, nullable=True)
    client_ts: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    server_ts: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=datetime.utcnow, index=True
    )

    user: Mapped[User] = relationship(foreign_keys=[user_id])
    team: Mapped[Team | None] = relationship(foreign_keys=[team_id])


class CameraDevice(Base):
    __tablename__ = "camera_devices"
    __table_args__ = (UniqueConstraint("device_id", name="uq_camera_devices_device_id"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    device_id: Mapped[str] = mapped_column(Text, nullable=False)
    secret_hash: Mapped[str] = mapped_column(Text, nullable=False)
    user_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id"), nullable=True, index=True
    )
    name: Mapped[str] = mapped_column(Text, nullable=False, default="")
    device_type: Mapped[str] = mapped_column(Text, nullable=False, default="maxicam")
    bound_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=datetime.utcnow
    )

    user: Mapped[User | None] = relationship(foreign_keys=[user_id])


class ChatMessage(Base):
    __tablename__ = "chat_messages"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    team_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("teams.id", ondelete="CASCADE"), nullable=False, index=True
    )
    sender_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id"), nullable=False, index=True
    )
    body: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=utc_now, index=True
    )

    team: Mapped[Team] = relationship(foreign_keys=[team_id])
    sender: Mapped[User] = relationship(foreign_keys=[sender_id])


class TeamChatRead(Base):
    __tablename__ = "team_chat_reads"
    __table_args__ = (UniqueConstraint("team_id", "user_id", name="uq_team_chat_reads_team_user"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    team_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("teams.id", ondelete="CASCADE"), nullable=False, index=True
    )
    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id"), nullable=False, index=True
    )
    last_read_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=datetime.utcnow
    )

    team: Mapped[Team] = relationship(foreign_keys=[team_id])
    user: Mapped[User] = relationship(foreign_keys=[user_id])


class ReportJob(Base):
    __tablename__ = "report_jobs"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    team_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("teams.id", ondelete="CASCADE"), nullable=False, index=True
    )
    subject_user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id"), nullable=False, index=True
    )
    requested_by_user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id"), nullable=False, index=True
    )
    report_date: Mapped[date] = mapped_column(Date, nullable=False, index=True)
    report_type: Mapped[str] = mapped_column(Text, nullable=False, default="safety")
    status: Mapped[str] = mapped_column(Text, nullable=False, default="pending", index=True)
    phase_detail: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
    timeline_json: Mapped[dict | None] = mapped_column(JSONB, nullable=True)
    markdown: Mapped[str | None] = mapped_column(Text, nullable=True)
    markdown_generated: Mapped[str | None] = mapped_column(Text, nullable=True)
    edited_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=datetime.utcnow
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=datetime.utcnow
    )
    finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    team: Mapped[Team] = relationship(foreign_keys=[team_id])
    subject_user: Mapped[User] = relationship(foreign_keys=[subject_user_id])
    requested_by: Mapped[User] = relationship(foreign_keys=[requested_by_user_id])


class CompanionState(Base):
    __tablename__ = "companion_states"

    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id"), primary_key=True
    )
    name: Mapped[str] = mapped_column(Text, nullable=False, default="小欧")
    mood: Mapped[str] = mapped_column(Text, nullable=False, default="idle")
    level: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
    energy: Mapped[int] = mapped_column(Integer, nullable=False, default=100)
    total_steps: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    message: Mapped[str] = mapped_column(Text, nullable=False, default="你好，我是小欧！")
    pose_json: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=datetime.utcnow
    )


class MotionSession(Base):
    __tablename__ = "motion_sessions"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id"), nullable=False, index=True
    )
    status: Mapped[str] = mapped_column(Text, nullable=False, default="active")
    started_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=datetime.utcnow
    )
    ended_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    steps: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    fall_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    pose_score: Mapped[float] = mapped_column(Float, nullable=False, default=0.0)
    summary_json: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=datetime.utcnow
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=datetime.utcnow
    )

    user: Mapped[User] = relationship(foreign_keys=[user_id])
    snapshots: Mapped[list["MotionSnapshot"]] = relationship(
        back_populates="session", cascade="all, delete-orphan"
    )


class MotionSnapshot(Base):
    __tablename__ = "motion_snapshots"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    session_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("motion_sessions.id", ondelete="CASCADE"), nullable=False, index=True
    )
    client_ts: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    steps: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    activity: Mapped[str] = mapped_column(Text, nullable=False, default="idle")
    fall_detected: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    pose_keypoints: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
    sensor_json: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
    server_ts: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=datetime.utcnow
    )

    session: Mapped[MotionSession] = relationship(back_populates="snapshots")
