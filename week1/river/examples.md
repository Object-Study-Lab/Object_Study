# 오브젝트 1, 2장 스터디용 실제 코드 예제 정리

이 문서는 실제 예약/결제 프로젝트에서 사용 중인 코드 예시를 바탕으로, 오브젝트 1장과 2장을 설명하기 쉽게 다시 정리한 자료다.  
특정 저장소나 서비스 배경지식이 없어도 읽을 수 있도록 설명은 일반화했고, 코드는 핵심 흐름만 발췌했다.

## 이 예제로 볼 수 있는 것

- 1장: 책임이 서비스에 몰릴 때 어떤 문제가 생기는가
- 2장: 역할과 다형성으로 협력을 어떻게 단순하게 만들 수 있는가
- 확장 토론: 역할을 도입했어도 현재 흐름 안에 남아 있는 타입 분기 문제

## 1장 예제: 책임이 서비스에 몰린 구조

오브젝트 1장에서 보기 좋은 포인트는 "객체가 자기 일을 스스로 하느냐"이다.  
이 예시에서는 실제 예약 생성, 결제 준비, 결제 승인, 취소 흐름의 중심이 되는 서비스와 스케줄러에 판단이 많이 모여 있다.

### 예시 1. 현재 예약/결제 흐름의 중심 서비스

현재 API는 예약 생성과 취소를 아래 서비스로 위임한다.

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments/toss/reservation")
public class ReservationController {

    private final TossReservationPaymentService tossReservationPaymentService;

    @PostMapping("/room/{roomId}")
    public ResponseEntity<Long> createGuesthouseReservation(...) {
        Long id = tossReservationPaymentService.createGuesthouseReservation(dto, roomId);
        return ResponseEntity.status(201).body(id);
    }

    @PostMapping("/party/{partyId}")
    public ResponseEntity<Long> createPartyReservation(...) {
        Long id = tossReservationPaymentService.createPartyReservation(dto, partyId);
        return ResponseEntity.status(201).body(id);
    }

    @DeleteMapping("{reservationId}")
    public ResponseEntity<?> cancelReservation(...) {
        if (dto.getType() == ReservationType.GUESTHOUSE) {
            return ResponseEntity.ok(tossReservationPaymentService.cancelGuesthouseReservation(dto, reservationId));
        } else {
            return ResponseEntity.ok(tossReservationPaymentService.cancelPartyReservation(dto, reservationId));
        }
    }
}
```

이 흐름의 중심 서비스는 아래와 같다.

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class TossReservationPaymentServiceImpl implements TossReservationPaymentService {

    private final ReservationRepository reservationRepository;
    private final TossPaymentRepository tossPaymentRepository;
    private final RoomInventoryRepository roomInventoryRepository;
    private final RoomInventoryInitializer roomInventoryInitializer;
    private final TossPaymentService tossPaymentService;
    private final GuesthouseRefundPolicyService refundPolicyService;
    private final AuthUtils authUtils;
    private final UserAuthenticationRepository authenticationRepository;
    private final RoomRepository roomRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PartyRepository partyRepository;
    private final PartyReservationRepository partyReservationRepository;
    private final PartyImageRepository partyImageRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponService couponService;
    private final PointService pointService;
    private final PaymentTxService paymentTxService;
}
```

#### 핵심 메서드

- `createGuesthouseReservation()`
- `createPartyReservation()`
- `cancelGuesthouseReservation()`
- `cancelPartyReservation()`
- `ready()`
- `confirm()`
- `validateReadyReservationState()`
- `validateConfirmReservationState()`

#### 메서드 발췌: `createGuesthouseReservation()`

```java
@Transactional
@Override
public Long createGuesthouseReservation(GReservationCreateReqDTO dto, Long roomId) {
    if (!dto.getCheckOut().isAfter(dto.getCheckIn())) {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "체크아웃 날짜는 체크인보다 이후여야 합니다.");
    }
    if (dto.getCheckIn().isBefore(LocalDate.now())) {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "과거 날짜에는 예약할 수 없습니다.");
    }

    User user = authUtils.getCurrentUser();
    Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "존재하지 않는 객실입니다."));

    if (room.getRoomStatus() != RoomStatus.OPEN) {
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "현재 예약할 수 없는 객실입니다.");
    }

    UserAuthentication auth = authenticationRepository.findByUser(user)
            .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "유저 인증 정보가 없습니다."));

    if (room.getRoomMaxCapacity() < dto.getGuestCount()) {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "최대 인원 수보다 예약 인원 수가 많습니다.");
    }

    validateRoomGender(room, auth.getGender(), user.getId());

    Reservation reservation = dto.toEntity(user, room);
    reserveRoomInventory(reservation);

    int maxRetries = 3;
    int retryCount = 0;
    Reservation savedReservation = null;

    while (retryCount < maxRetries) {
        try {
            savedReservation = reservationRepository.save(reservation);
            break;
        } catch (DataIntegrityViolationException e) {
            retryCount++;
            if (retryCount >= maxRetries) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "예약 코드 생성에 실패했습니다.");
            }
            reservation.updateReservationCode(ReservationCodeGenerator.generate());
        }
    }

    return savedReservation.getId();
}
```

#### 메서드 발췌: `cancelGuesthouseReservation()`

```java
public ReservationDetailResDTO cancelGuesthouseReservation(ReservationCancelReqDTO dto, Long reservationId) {
    if (dto.getType() != ReservationType.GUESTHOUSE) {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "게스트하우스 예약만 취소할 수 있습니다.");
    }

    Reservation reservation = getReservation(reservationId);
    User user = authUtils.getCurrentUser();

    if (!reservation.getUser().getId().equals(user.getId())) {
        throw new BusinessException(ErrorCode.ACCESS_DENIED, "사용자 불일치");
    }

    if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "확정된 예약만 취소할 수 있습니다.");
    }

    TossPayment tossPayment = tossPaymentRepository
            .findFirstByReservationIdAndReservationTypeAndTossPaymentStatusOrderByApprovedAtDesc(
                    reservation.getId(),
                    ReservationType.GUESTHOUSE,
                    TossPaymentStatus.DONE
            )
            .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "결제 내역 없음"));

    int refundRate = refundPolicyService.calculateRefundRate(
            reservation.getRoom().getGuesthouse().getId(),
            reservation.getCheckIn(),
            LocalDate.now()
    );

    Long cancelAmount = calculateCancelAmount(BigDecimal.valueOf(tossPayment.getTotalAmount()), refundRate);
    // 외부 결제 취소 요청, 상태 변경, 알림/로그 발행 ...
}
```

#### 이 예시로 설명할 수 있는 점

- 현재 실제 예약 생성 흐름이 이 서비스를 지난다.
- 날짜 검증, 상태 검증, 인증 조회, 재고 확보, 예약 코드 재시도, 결제 준비, 결제 승인, 취소, 환불, 쿠폰, 포인트, 알림까지 모두 이 서비스에 모여 있다.
- 하나의 서비스가 여러 하위 정책을 너무 많이 알고 있어, 절차를 길게 조립하는 구조가 된다.
- 객체들은 상태를 들고 있지만, 중요한 판단은 서비스가 수행하는 경우가 많다.

#### 질문

- 예약 생성 규칙, 결제 규칙, 취소 규칙, 환불 규칙이 한 서비스에 함께 있어야 할까?
- `Reservation`, `PartyReservation`, `Room`, `Party`가 스스로 더 많은 판단을 하게 만들 수는 없을까?
- 이 서비스는 조율자에 가까운가, 아니면 도메인 규칙 저장소에 가까운가?

### 예시 2. 예약 만료 정리 스케줄러

스케줄러도 단순 실행기라기보다 도메인 규칙을 직접 수행하고 있다.

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationScheduler {

    private final ReservationRepository reservationRepository;
    private final PartyReservationRepository partyReservationRepository;
    private final TossPaymentRepository tossPaymentRepository;
    private final RoomInventoryRepository roomInventoryRepository;
    private final UserAuthenticationRepository userAuthenticationRepository;

    private static final long PENDING_TTL_MINUTES = 5L;
}
```

#### 핵심 메서드

- `cleanupExpiredPendingReservations()`
- `cleanupGuesthouseReservations()`
- `cleanupPartyReservations()`
- `releaseRoomInventory()`
- `updateGuesthouseReservations()`
- `updatePartyReservations()`

#### 메서드 발췌: `cleanupPartyReservations()`

```java
private void cleanupPartyReservations(LocalDateTime cutoff) {
    List<PartyReservation> expiredReservations = partyReservationRepository
            .findByStatusAndCreatedAtBefore(ReservationStatus.PENDING, cutoff);

    for (PartyReservation reservation : expiredReservations) {
        if (isPaymentInProgress(reservation.getId(), cutoff)) {
            continue;
        }

        cancelRelatedPayments(reservation.getId(), cutoff);

        Party party = reservation.getParty();
        UserAuthentication auth = userAuthenticationRepository.findByUser(reservation.getUser())
                .orElse(null);

        if (auth != null) {
            if (auth.getGender() == Gender.M) {
                party.decreaseMaleAttendees();
            } else {
                party.decreaseFemaleAttendees();
            }
        }

        int currentAttendees = party.getMaleAttendees() + party.getFemaleAttendees();
        if (party.getPartyStatus() == PartyStatus.RECRUIT_END
                && currentAttendees < party.getMaxAttendees()
                && party.getPartyStartDateTime().isAfter(LocalDateTime.now())) {
            party.changeStatus(PartyStatus.RECRUIT);
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
    }

    if (!expiredReservations.isEmpty()) {
        partyReservationRepository.saveAll(expiredReservations);
    }
}
```

#### 이 예시로 설명할 수 있는 점

- 스케줄러가 결제 진행 여부, 인원 감소, 모집 상태 복구, 예약 상태 변경까지 모두 처리한다.
- 후처리 정책이 객체 간 협력으로 분산되지 않고, 하나의 절차 안에 집중되어 있다.
- 1장 관점에서는 "행동의 주체가 객체가 아니라 서비스/스케줄러"인 구조로 볼 수 있다.

### 1장 정리

이 예시는 실제로 사용 중인 코드이면서도, 책임이 서비스와 스케줄러에 많이 몰린 구조를 잘 보여준다.  
그래서 "동작은 하지만 객체의 자율성과 캡슐화 측면에서는 아쉬움이 있는 구조"를 설명하기 좋다.

## 2장 예제: 역할과 다형성으로 협력하기

오브젝트 2장의 핵심은 "구체 클래스보다 역할이 더 중요하다"는 점이다.  
아래 예시에서는 `Reservable` 인터페이스가 바로 그 역할을 담당한다.

### 예시 1. 공통 역할 `Reservable`

결제 서비스 입장에서 중요한 것은 "어떤 예약인가"보다 "결제 가능한 예약인가"이다.

```java
public interface Reservable {
    Long getId();
    ReservationStatus getStatus();
    BigDecimal getAmount();
    String getRequest();
    User getUser();
    void setStatus(ReservationStatus status);
}
```

#### 이 인터페이스가 의미하는 것

- 예약 ID를 알 수 있어야 한다.
- 현재 예약 상태를 알 수 있어야 한다.
- 결제 금액을 알 수 있어야 한다.
- 예약자 정보를 알 수 있어야 한다.
- 상태를 변경할 수 있어야 한다.

즉, 결제 맥락에서 필요한 공통 메시지를 역할로 추상화한 것이다.

### 예시 2. 같은 역할을 수행하는 두 클래스

게스트하우스 예약과 파티 예약은 서로 다른 도메인 객체지만, 결제 맥락에서는 같은 역할을 수행할 수 있다.

#### `Reservation`

```java
@Builder
@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Reservation extends BaseEntity implements Reservable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.PENDING;

    @Column(nullable = false)
    private String request;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
```

#### `PartyReservation`

```java
@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PartyReservation extends BaseEntity implements Reservable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.PENDING;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
```

#### 취소 메서드 예시

```java
public void cancel() {
    this.status = ReservationStatus.CANCELLED;
    this.cancelledAt = LocalDateTime.now();
    if (this.cancelledByType == null) {
        this.cancelledByType = ReservationCancelledByType.USER;
    }
}
```

```java
public void cancel() {
    this.status = ReservationStatus.CANCELLED;
    this.cancelledAt = LocalDateTime.now();
}
```

#### 이 예시로 설명할 수 있는 점

- 두 클래스는 서로 다르지만 같은 역할에 응답한다.
- 협력하는 쪽은 "게스트하우스 예약인지 파티 예약인지"보다 "예약 역할을 수행하는가"에 집중할 수 있다.
- 이것이 역할 중심 설계, 다형성, 협력의 장점이다.

### 예시 3. 역할에 의존하는 결제 서비스

아래 예시는 결제 서비스가 구체 클래스 대신 `Reservable` 역할에 의존하는 장면이다.

#### 메서드 발췌: `ready()`

```java
@Transactional
public TossPaymentReadyResDTO ready(TossPaymentReadyReqDTO req) {
    Reservable reservable = getReservableEntity(req.reservationId(), req.reservationType());

    validateReadyReservationState(reservable, req.reservationType());

    User user = authUtils.getCurrentUser();
    validateUser(user.getId(), reservable);

    if (tossPaymentRepository.existsByReservationIdAndReservationTypeAndTossPaymentStatusIn(
            reservable.getId(),
            req.reservationType(),
            ACTIVE_STATUSES
    )) {
        throw new BusinessException(ErrorCode.ALREADY_PROCESSED, "이미 결제가 진행된 예약입니다.");
    }

    Long originalAmount = extractAmount(reservable);
    Long couponDiscount = calculateCouponDiscount(originalAmount, req.userCouponId(), user.getId());
    Long pointDiscount = req.pointUsed() != null ? req.pointUsed().longValue() : 0L;

    Long amountAfterCoupon = originalAmount - couponDiscount;
    if (pointDiscount > amountAfterCoupon) {
        pointDiscount = amountAfterCoupon;
    }

    Long finalAmount = amountAfterCoupon - pointDiscount;
    return tossPaymentService.ready(
            reservable.getId(),
            user.getId(),
            finalAmount,
            req.reservationType(),
            couponDiscount,
            pointDiscount,
            req.userCouponId()
    );
}
```

#### 메서드 발췌: `getReservableEntity()`

```java
private Reservable getReservableEntity(Long reservationId, ReservationType type) {
    if (type == ReservationType.GUESTHOUSE) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "게스트하우스 예약을 찾을 수 없습니다."));
    } else if (type == ReservationType.PARTY) {
        return partyReservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "파티 예약을 찾을 수 없습니다."));
    }
    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "지원하지 않는 예약 타입입니다.");
}
```

#### 메서드 발췌: `validateReadyReservationState()`

```java
private void validateReadyReservationState(Reservable reservable, ReservationType reservationType) {
    if (reservable.getStatus() == ReservationStatus.PENDING) {
        return;
    }

    if (isZeroAmountPartyReservation(reservable, reservationType)
            && reservable.getStatus() == ReservationStatus.CONFIRMED) {
        return;
    }

    throw new BusinessException(ErrorCode.STATE_CONFLICT, "결제 가능한 예약 상태가 아닙니다.");
}
```

#### 이 예시로 설명할 수 있는 점

- 결제 서비스는 공통 메시지인 `getStatus()`, `getAmount()`, `getUser()`에 의존한다.
- 그래서 구체 클래스가 달라도 같은 결제 흐름을 재사용할 수 있다.
- 이것이 "역할이 협력의 중심이 된다"는 2장의 핵심과 잘 맞는다.

### 2장 정리

이 예시는 서로 다른 예약 객체를 `Reservable`이라는 하나의 역할로 묶어, 결제 시점의 협력을 단순하게 만드는 구조를 보여준다.

## 확장 토론: 아직 남아 있는 타입 분기

이 예제는 역할 기반 설계가 잘 드러나지만, 완전히 다형성만으로 정리된 구조는 아니다.  
현재 토스 흐름 안에서도 여전히 `ReservationType` 분기가 남아 있다.

```java
private Reservable getReservableEntity(Long reservationId, ReservationType type) {
    if (type == ReservationType.GUESTHOUSE) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "게스트하우스 예약을 찾을 수 없습니다."));
    } else if (type == ReservationType.PARTY) {
        return partyReservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "파티 예약을 찾을 수 없습니다."));
    }
    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "지원하지 않는 예약 타입입니다.");
}
```

### 관련 메서드 예시: 예약 타입별 취소 분기

```java
@DeleteMapping("{reservationId}")
public ResponseEntity<?> cancelReservation(@RequestBody @Valid ReservationCancelReqDTO dto, Long reservationId) {
    if (dto.getType() == ReservationType.GUESTHOUSE) {
        return ResponseEntity.ok(tossReservationPaymentService.cancelGuesthouseReservation(dto, reservationId));
    } else {
        return ResponseEntity.ok(tossReservationPaymentService.cancelPartyReservation(dto, reservationId));
    }
}
```

### 토론 포인트

- `Reservable`을 도입했는데도 왜 타입 분기가 여전히 남아 있을까?
- 조회 단계의 분기와 도메인 정책 분기를 어떻게 구분해서 볼 수 있을까?
- 취소, 상태 검증, 금액 계산 중 어디까지를 역할로 흡수할 수 있을까?
- 역할 인터페이스를 더 세분화하거나, 취소 정책을 별도 객체로 분리할 수 있을까?

## 마무리

- 1장에서는 실제로 사용 중인 예약/결제 서비스와 스케줄러를 통해 책임 집중 문제를 이야기할 수 있다.
- 2장에서는 `Reservable`을 통해 역할, 책임, 협력, 다형성을 설명할 수 있다.
- 마지막으로 현재 토스 흐름에도 남아 있는 타입 분기를 통해, 현실의 코드에서는 좋은 설계와 과도기적 설계가 함께 존재한다는 점까지 자연스럽게 연결할 수 있다.

