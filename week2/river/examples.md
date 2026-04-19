# 오브젝트 3, 4장 스터디용 실제 코드 예제 정리

이 문서는 실제 예약/결제 프로젝트에서 사용 중인 코드 예시를 바탕으로, 오브젝트 3장과 4장을 설명하기 쉽게 다시 정리한 자료다.  
특정 저장소나 서비스 배경지식이 없어도 읽을 수 있도록 설명은 일반화했고, 코드는 핵심 흐름만 발췌했다.

## 이 예제로 볼 수 있는 것

- 3장: 객체가 메시지에 응답하며 협력하는 구조
- 4장: 데이터 중심 설계가 남아 있을 때 어떤 냄새가 생기는가
- 확장 토론: 같은 프로젝트 안에서도 책임 중심 코드와 데이터 중심 코드가 섞여 있다는 점

## 3장 예제: 책임과 협력이 보이는 코드

오브젝트 3장에서 보기 좋은 포인트는 "누가 이 일을 해야 하는가"이다.  
아래 예시들은 실제 코드에서 객체가 자기 규칙을 직접 처리하는 부분들이다.

### 예시 1. 재고 규칙을 스스로 지키는 `RoomInventory`

`RoomInventory`는 단순 수량 보관 객체가 아니다.  
예약 가능 여부, 마감 여부, 판매 가능 수량 변경 규칙을 직접 처리한다.

```java
@Entity
@Builder
@Getter
public class RoomInventory extends BaseEntity {

    @Column(name = "capacity_reserved", nullable = false)
    private Integer capacityReserved = 0;

    @Column(name = "sellable_capacity", nullable = false)
    private Integer sellableCapacity;

    @Column(name = "is_closed", nullable = false)
    private Boolean isClosed = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    public int remaining() {
        return sellableCapacity - capacityReserved;
    }

    public void reserve(Integer guestCount) {
        if (guestCount == null || guestCount < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "[ERROR] guestCount는 1 이상이어야 합니다.");
        }
        if (Boolean.TRUE.equals(isClosed)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "[ERROR] 마감된 날짜에는 예약할 수 없습니다.");
        }
        if (capacityReserved + guestCount > sellableCapacity) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "[ERROR] 잔여 인원이 부족합니다.");
        }
        this.capacityReserved += guestCount;
    }

    public void cancel(Integer guestCount) {
        if (guestCount == null || guestCount < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "[ERROR] guestCount는 1 이상이어야 합니다.");
        }
        int next = capacityReserved - guestCount;
        if (next < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "[ERROR] capacityReserved는 0 미만이 될 수 없습니다.");
        }
        this.capacityReserved = next;
    }

    public void updateSellableCapacity(int nextSellableCapacity) {
        if (nextSellableCapacity < this.capacityReserved) {
            throw new BusinessException(ErrorCode.ROOM_AVAILABLE_BEDS_BELOW_RESERVED, "예약 가능 베드 수는 현재 예약보다 작을 수 없습니다.");
        }
        if (nextSellableCapacity > room.getRoomMaxCapacity()) {
            throw new BusinessException(ErrorCode.ROOM_AVAILABLE_BEDS_EXCEEDS_MAX, "객실 최대 베드 수를 초과할 수 없습니다.");
        }
        this.sellableCapacity = nextSellableCapacity;
    }
}
```

#### 이 예시로 설명할 수 있는 점

- 재고 규칙이 서비스 밖이 아니라 객체 안에 모여 있다.
- 서비스는 "예약해라", "취소해라", "수량을 바꿔라"라고 메시지를 보내는 쪽에 가깝다.
- 3장 관점에서는 책임이 정보를 가진 객체로 이동한 예시로 볼 수 있다.

### 예시 2. 결제 상태 전이를 직접 표현하는 `TossPayment`

결제 객체도 상태를 직접 들고 있을 뿐 아니라, 상태 전이를 도메인 언어로 드러낸다.

```java
@Entity
@Getter
@Builder
public class TossPayment {

    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    private TossPaymentStatus tossPaymentStatus;

    @Enumerated(value = EnumType.STRING)
    @Column(name = "payment_flow_status")
    private PaymentFlowStatus paymentFlowStatus;

    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private Long cancelledAmount;
    private String cancelReason;
    private LocalDateTime cancelledAt;

    public void markPending() {
        this.tossPaymentStatus = TossPaymentStatus.PENDING;
        this.paymentFlowStatus = PaymentFlowStatus.READY;
        this.requestedAt = LocalDateTime.now();
    }

    public void approve(String paymentKey, TossPaymentMethod method, LocalDateTime approvedAt) {
        this.tossPaymentKey = paymentKey;
        this.tossPaymentMethod = method;
        this.tossPaymentStatus = TossPaymentStatus.DONE;
        this.paymentFlowStatus = PaymentFlowStatus.CONFIRMED;
        this.approvedAt = approvedAt;
    }

    public void fail() {
        this.tossPaymentStatus = TossPaymentStatus.FAILED;
        this.paymentFlowStatus = PaymentFlowStatus.FAILED;
    }

    public void cancel(TossPaymentStatus status, Long cancelledAmount, LocalDateTime cancelledAt, String cancelReason) {
        this.tossPaymentStatus = status;
        this.paymentFlowStatus = PaymentFlowStatus.CANCELLED;
        this.cancelledAmount = cancelledAmount;
        this.cancelledAt = cancelledAt;
        this.cancelReason = cancelReason;
    }
}
```

#### 이 예시로 설명할 수 있는 점

- `approve()`, `fail()`, `cancel()`은 단순 setter보다 의도가 분명하다.
- 협력하는 쪽은 필드 조합을 몰라도 메시지만 보내면 된다.
- 3장에서 말하는 "메시지가 먼저다"를 설명하기 좋다.

### 예시 3. 여러 객체가 협력하는 결제 확정 서비스

좋은 협력은 서비스가 없어지는 것이 아니라, 서비스가 조율자 역할에 머무르는 것이다.  
아래 흐름은 결제 서비스가 여러 객체를 협력시키는 장면이다.

```java
private void processCommonConfirmation(TossPayment payment) {
    if (payment.getReservationType() == ReservationType.GUESTHOUSE) {
        Reservation reservation = reservationRepository.findById(payment.getReservationId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "존재하지 않는 예약입니다."));
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservationRepository.save(reservation);
        reservationRefundPolicySnapshotService.captureForReservation(reservation.getId());
    } else {
        PartyReservation reservation = partyReservationRepository.findById(payment.getReservationId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "존재하지 않는 예약입니다."));
        reservation.setStatus(ReservationStatus.CONFIRMED);
        partyReservationRepository.save(reservation);
    }

    if (payment.getUserCouponId() != null) {
        couponService.useCoupon(payment.getUserCouponId(), payment);
    }

    if (payment.getPointDiscountAmount() > 0) {
        BigDecimal pointAmount = BigDecimal.valueOf(payment.getPointDiscountAmount());
        pointService.spendPoints(
                payment.getUserId(),
                pointAmount,
                "예약 결제 사용",
                PointRelatedEntityType.PAYMENT,
                payment.getId()
        );
    }
}
```

#### 이 예시로 설명할 수 있는 점

- 하나의 유스케이스 안에서 예약, 결제, 쿠폰, 포인트가 협력한다.
- 결제 서비스는 협력의 조율자 역할을 한다.
- 다만 예약 확정은 여전히 `setStatus()`를 쓰기 때문에, 좋은 협력 안에도 데이터 중심 요소가 남아 있음을 같이 볼 수 있다.

### 예시 4. 비동기 협력과 Outbox

협력은 직접 호출만이 아니라 메시지 기반으로도 이뤄질 수 있다.

```java
@Component
@RequiredArgsConstructor
public class ReservationEmailEventListener {

    private final ReservationEmailOutboxService outboxService;

    @Async("eventExecutor")
    @EventListener
    public void handleReservationEmailEvent(ReservationEmailEvent event) {
        outboxService.saveOutboxEvent(event);
    }
}
```

```java
@Entity
@Getter
public class ReservationEmailOutbox {

    private boolean processed = false;
    private LocalDateTime processedAt;

    public void markAsProcessed() {
        this.processed = true;
        this.processedAt = LocalDateTime.now();
    }
}
```

#### 이 예시로 설명할 수 있는 점

- 발신자와 수신자가 강하게 결합되지 않는다.
- 이벤트를 통해 협력하지만, 상태 변경 책임은 여전히 객체가 가진다.
- 3장 관점에서는 "메시지를 통한 협력"의 좋은 실무 예시로 쓸 수 있다.

### 3장 정리

이 예시들은 실제 프로젝트 안에서도 책임 중심 설계가 충분히 가능하다는 점을 보여준다.  
특히 `RoomInventory`, `TossPayment`, `ReservationEmailOutbox`는 "객체가 스스로 행동한다"는 설명에 잘 맞는다.

## 4장 예제: 데이터 중심 설계의 냄새가 보이는 코드

오브젝트 4장에서 보기 좋은 포인트는 "겉으로는 메서드가 있어도 실제로는 데이터 복사 구조일 수 있다"는 점이다.  
아래 예시들은 객체가 스스로 행동하기보다 외부 서비스와 매퍼가 필드를 조합하는 장면들이다.

### 예시 1. 긴 파라미터 리스트로 상태를 복사하는 `Guesthouse.updateBasicInfo()`

메서드 이름은 행위처럼 보이지만, 실제로는 필드 패치 함수에 가깝다.

```java
public class Guesthouse extends BaseEntity {

    public void updateBasicInfo(String name, String address, String phone,
                                String shortIntro, String longDesc, LocalTime checkIn,
                                LocalTime checkOut, String rules, String refundPolicyAdditionalNotice, String addressDetail) {
        if (name != null) this.guesthouseName = name;
        if (address != null) this.guesthouseAddress = address;
        if (phone != null) this.guesthousePhone = phone;
        if (shortIntro != null) this.guesthouseShortIntro = shortIntro;
        if (longDesc != null) this.guesthouseLongDescription = longDesc;
        if (checkIn != null) this.checkIn = checkIn;
        if (checkOut != null) this.checkOut = checkOut;
        if (rules != null) this.rules = rules;
        if (refundPolicyNotice != null) this.refundPolicyNotice = refundPolicyAdditionalNotice;
        if (addressDetail != null) this.guesthouseDetailAddress = addressDetail;
    }
}
```

그리고 이 메서드는 매퍼에서 DTO 필드를 그대로 받아 호출된다.

```java
@Component
public class UpdateMapper {

    public void guesthouseUpdate(GuesthouseUpdateReqDTO dto, Guesthouse guesthouse) {
        guesthouse.updateBasicInfo(
                dto.getGuesthouseName(),
                dto.getGuesthouseAddress(),
                dto.getGuesthousePhone(),
                dto.getGuesthouseShortIntro(),
                dto.getGuesthouseLongDescription(),
                dto.getCheckIn(),
                dto.getCheckOut(),
                dto.getRules(),
                dto.getRefundPolicyNotice(),
                dto.getGuesthouseDetailAddress()
        );
    }
}
```

#### 이 예시로 설명할 수 있는 점

- 호출자가 객체 내부 필드 구조를 많이 알아야 한다.
- `updateBasicInfo()`는 의미 있는 도메인 메시지보다 "묶여 있는 setter"에 가깝다.
- 실제로 null 체크 변수와 대입 변수가 어긋나 있어 버그 가능성도 생긴다.

### 예시 2. DTO 의존이 드러나는 `Room.update()`와 `Party.updateFromDTO()`

이런 메서드는 객체의 책임을 드러내기보다 입력값 복사에 초점이 맞춰져 있다.

```java
public class Room extends BaseEntity {

    public void update(String roomName, RoomType roomType, GenderType dormitoryGenderType, Boolean femaleOnly,
                       Integer capacity, Integer maxCapacity, String desc, BigDecimal price) {
        if (roomName != null) this.roomName = roomName;
        if (roomType != null) this.roomType = roomType;
        if (dormitoryGenderType != null) this.dormitoryGenderType = dormitoryGenderType;
        if (femaleOnly != null) this.femaleOnly = femaleOnly;
        if (capacity != null) this.roomCapacity = capacity;
        if (maxCapacity != null) this.roomMaxCapacity = maxCapacity;
        if (desc != null) this.roomDescription = desc;
        if (price != null) this.roomPrice = price;
    }
}
```

```java
public class Party extends BaseEntity {

    public void updateFromDTO(PartyUpdateReqDTO requestDTO, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        partyTitle = requestDTO.getPartyTitle();
        partyTags = requestDTO.getTags();
        partyStartTime = requestDTO.getPartyStartTime();
        partyEndTime = requestDTO.getPartyEndTime();
        maxAttendees = requestDTO.getMaxAttendees();
        minAttendees = requestDTO.getMinAttendees();
        isGuest = requestDTO.getIsGuest();
        amount = Optional.ofNullable(requestDTO.getAmount()).orElse(BigDecimal.ZERO);
        femaleAmount = Optional.ofNullable(requestDTO.getFemaleAmount()).orElse(BigDecimal.ZERO);
        maleNonAmount = Optional.ofNullable(requestDTO.getMaleNonAmount()).orElse(BigDecimal.ZERO);
        femaleNonAmount = Optional.ofNullable(requestDTO.getFemaleNonAmount()).orElse(BigDecimal.ZERO);
        partyStartDateTime = startDateTime;
        partyEndDateTime = endDateTime;
        detailSchedule = requestDTO.getDetailSchedule();
        snacks = requestDTO.getSnacks();
        extraInfo = requestDTO.getExtraInfo();
        meetingPlace = requestDTO.getMeetingPlace();
        trafficInfo = requestDTO.getTrafficInfo();
        parkingInfo = requestDTO.getParkingInfo();
    }
}
```

#### 이 예시로 설명할 수 있는 점

- 객체 메서드 안으로 DTO가 직접 들어오면 계층 결합이 생긴다.
- 메서드 이름이 책임보다 입력 포맷을 설명한다.
- 4장 관점에서는 데이터 중심 설계의 냄새가 강한 예시다.

### 예시 3. tell-don't-ask를 위반하는 `UserPoint`

`UserPoint`는 포인트 도메인 객체지만, 실제 계산은 서비스가 담당한다.

```java
@Entity
@Getter
@Builder
public class UserPoint extends BaseEntity {

    @Column(name = "current_points", precision = 15, scale = 2, nullable = false)
    private BigDecimal currentPoints = BigDecimal.ZERO;

    public void updateUserPoint(BigDecimal currentPoints) {
        this.currentPoints = currentPoints;
    }
}
```

서비스는 현재 값을 꺼낸 뒤 계산해서 다시 넣는다.

```java
@Transactional
public void spendPoints(Long userId, BigDecimal amountToSpend, String description, PointRelatedEntityType relatedType, Long relatedId) {
    if (amountToSpend.compareTo(BigDecimal.ZERO) <= 0) return;

    UserPoint userPoint = getOrCreateUserPoint(userId);

    if (userPoint.getCurrentPoints().compareTo(amountToSpend) < 0) {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "포인트 잔액이 부족합니다.");
    }

    userPoint.updateUserPoint(userPoint.getCurrentPoints().subtract(amountToSpend));
    deductFifoPoints(userId, amountToSpend);
    pointTransactionService.recordSpend(userId, amountToSpend, description, relatedType, relatedId);
}
```

반면 포인트 거래 객체는 자기 책임을 조금 더 직접 수행한다.

```java
public class PointTransaction extends BaseEntity {

    @Column(name = "remaining_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal remainingAmount;

    public void spendPoint(BigDecimal amount){
        this.remainingAmount = this.remainingAmount.subtract(amount);
    }
}
```

#### 이 예시로 설명할 수 있는 점

- 같은 포인트 도메인 안에서도 책임 중심 코드와 데이터 중심 코드가 섞여 있다.
- `UserPoint`는 외부가 상태를 물어보고 계산하는 구조다.
- `PointTransaction`은 자기 상태 변경 책임을 조금 더 객체 안에 둔다.

### 예시 4. 이름과 책임이 어긋나는 `UserCoupon`

메서드 이름이 도메인 행위를 잘 설명하지 못하면 객체 의도가 흐려진다.

```java
@Entity
@Getter
@Builder
public class UserCoupon extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserCouponStatus status;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "applied_payment_id")
    private Long appliedPaymentId;

    public void changeStatus(UserCouponStatus status) {
        this.status = status;
    }

    public void setCouponTemplate(UserCouponStatus status, LocalDateTime usedAt, Long appliedPaymentId) {
        this.status = status;
        this.usedAt = usedAt;
        this.appliedPaymentId = appliedPaymentId;
    }
}
```

서비스는 상태 검증 뒤, 의미 있는 행위를 보내기보다 값을 조합해서 넘긴다.

```java
@Transactional
public void useCoupon(Long userCouponId, TossPayment payment) {
    UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "쿠폰을 찾을 수 없습니다."));

    if (userCoupon.getStatus() != UserCouponStatus.ISSUED) {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이미 사용되었거나 만료된 쿠폰입니다.");
    }
    if (LocalDateTime.now().isAfter(userCoupon.getExpiredAt())) {
        userCoupon.changeStatus(UserCouponStatus.EXPIRED);
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "만료된 쿠폰입니다.");
    }

    userCoupon.setCouponTemplate(UserCouponStatus.USED, LocalDateTime.now(), payment.getId());
}
```

#### 이 예시로 설명할 수 있는 점

- `setCouponTemplate()`는 이름과 실제 책임이 맞지 않는다.
- `use()`, `expire()`, `restore()` 같은 메시지가 더 역할 중심적이다.
- 4장 관점에서는 데이터 변경을 우회적으로 표현한 메서드로 읽을 수 있다.

### 예시 5. `Reservation`의 setter가 열어 둔 캡슐화 문제

도메인 메서드가 일부 있어도, 핵심 상태 전이가 setter로 열려 있으면 캡슐화가 약해진다.

```java
@Builder
@Entity
@Getter
public class Reservation extends BaseEntity implements Reservable {

    @Setter
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.PENDING;

    private LocalDateTime cancelledAt;
    private ReservationCancelledByType cancelledByType;
    private String cancelledReason;

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        if (this.cancelledByType == null) {
            this.cancelledByType = ReservationCancelledByType.USER;
        }
    }

    public void cancel(ReservationCancelledByType cancelledByType, String cancelledReason) {
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancelledByType = cancelledByType;
        this.cancelledReason = cancelledReason;
    }
}
```

그런데 실제 결제 확정 흐름에서는 아래처럼 setter가 그대로 사용된다.

```java
if (payment.getReservationType() == ReservationType.GUESTHOUSE) {
    Reservation reservation = reservationRepository.findById(payment.getReservationId())
            .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "존재하지 않는 예약입니다."));
    reservation.setStatus(ReservationStatus.CONFIRMED);
    reservationRepository.save(reservation);
}
```

#### 이 예시로 설명할 수 있는 점

- 일부 행위는 객체가 직접 처리하지만, 일부 핵심 상태 변경은 외부에서 직접 만진다.
- 결과적으로 객체의 상태 전이 규칙이 한곳에 모이지 않는다.
- 4장 관점에서는 캡슐화가 절반만 적용된 구조로 읽을 수 있다.

### 4장 정리

이 예시들은 실무 코드가 흔히 "객체처럼 보이지만 사실은 데이터 구조에 가까운 상태"로 머무는 순간을 잘 보여준다.  
그래서 3장의 책임 중심 설계와 4장의 데이터 중심 설계를 비교하기에 적절하다.

## 같이 던지면 좋은 질문

1. `RoomInventory`는 왜 객체답고, `UserPoint`는 왜 덜 객체답게 느껴질까
2. `updateBasicInfo()`나 `updateFromDTO()`는 정말 행위 메서드라고 볼 수 있을까
3. 결제 서비스는 적절한 조율자인가, 아니면 너무 많은 책임을 알고 있는가
4. 이 코드베이스에서 가장 먼저 tell-don't-ask로 바꾸면 좋은 객체는 무엇일까
