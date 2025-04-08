# E-커머스 백엔드 API 시나리오 (개선안)

## 1. 사용자(User) 관련 API

### 1.1 사용자 등록 API
- **엔드포인트**: `POST /api/v1/users`
- **요청 데이터**:
  ```json
  {
    "email": "user@example.com",
    "name": "홍길동"
  }
  ```
- **응답 상태 코드**: `201 Created`
- **응답 헤더**: `Location: /api/v1/users/1`

### 1.2 사용자 조회 API
- **엔드포인트**: `GET /api/v1/users/{userId}`
- **응답 상태 코드**: `200 OK`
- **응답 데이터**:
  ```json
  {
    "email": "user@example.com",
    "name": "홍길동"
  }

## 2. 상점(Shop) 관련 API

### 2.1 상점 목록 조회 API
- **엔드포인트**: `GET /api/v1/shops`
- **쿼리 파라미터**: 
  - `category` (선택적): KOREAN, JAPANESE, CHINESE, WESTERN 등
  - `isOpen` (선택적): true/false
  - `limit` (선택적, 기본값: 20): 페이지당 결과 수 
  - `offset` (선택적, 기본값: 0): 페이지네이션 오프셋
- **응답 상태 코드**: `200 OK`
- **응답 데이터**:
  ```json
  {
    "shops": [
      {
        "id": 1,
        "name": "맛있는 한식당",
        "isOpen": true
      },
      {
        "id": 2,
        "name": "신선한 초밥집",
        "isOpen": false
      }
    ]
  }
  ```
- **용도**: 메인 페이지에서 모든 상점 목록 표시, 카테고리별 상점 필터링

### 2.2 상점 상세 조회 API
- **엔드포인트**: `GET /api/v1/shops/{shopId}`
- **응답 상태 코드**: `200 OK`
- **응답 데이터**:
  ```json
  {
    "id": 1,
    "name": "맛있는 한식당",
    "category": "KOREAN",
    "isOpen": true,
    "averageRating": 4.5,
    "links": [
      { "rel": "self", "href": "/api/v1/shops/1" },
      { "rel": "cartMenus", "href": "/api/v1/shops/1/cartMenus" },
      { "rel": "reviews", "href": "/api/v1/shops/1/reviews" }
    ]
  }
  ```
- **오류 응답(404)**:
  ```json
  {
    "error": {
      "code": "ShopNotFound",
      "message": "The shop with ID 1 was not found."
    }
  }
  ```
- **용도**: 상점 상세 페이지에서 상점 정보 표시

### 2.3 상점 생성 API (관리자용)
- **엔드포인트**: `POST /api/v1/shops`
- **요청 데이터**:
  ```json
  {
    "name": "맛있는 한식당",
    "category": "KOREAN"
  }
  ```
- **응답 상태 코드**: `201 Created`
- **응답 헤더**: `Location: /api/v1/shops/1`
- **응답 데이터**:
  ```json
  {
    "id": 1,
    "name": "맛있는 한식당",
    "category": "KOREAN",
    "isOpen": false,
    "links": [
      { "rel": "self", "href": "/api/v1/shops/1" },
      { "rel": "cartMenus", "href": "/api/v1/shops/1/cartMenus" }
    ]
  }
  ```
- **오류 응답(400)**:
  ```json
  {
    "error": {
      "code": "InvalidInput",
      "message": "The shop information provided is invalid.",
      "details": [
        { "field": "name", "message": "Shop name is required" }
      ]
    }
  }
  ```
- **용도**: 관리자 페이지에서 새 상점 등록

### 2.4 상점 영업 상태 변경 API (관리자용)
- **엔드포인트**: `PATCH /api/v1/shops/{shopId}`
- **요청 데이터**:
  ```json
  {
    "isOpen": true
  }
  ```
- **응답 상태 코드**: `200 OK`
- **응답 데이터**:
  ```json
  {
    "id": 1,
    "name": "맛있는 한식당",
    "category": "KOREAN",
    "isOpen": true,
    "links": [
      { "rel": "self", "href": "/api/v1/shops/1" },
      { "rel": "cartMenus", "href": "/api/v1/shops/1/cartMenus" }
    ]
  }
  ```
- **용도**: 상점 관리자가 영업 시작/종료 상태 변경

## 3. 메뉴(Menu) 관련 API

### 3.1 상점별 메뉴 목록 조회 API
- **엔드포인트**: `GET /api/v1/shops/{shopId}/cartMenus`
- **쿼리 파라미터**:
  - `isSoldOut` (선택적): true/false
  - `limit` (선택적, 기본값: 50): 페이지당 결과 수
  - `offset` (선택적, 기본값: 0): 페이지네이션 오프셋
- **응답 상태 코드**: `200 OK`
- **응답 데이터**:
  ```json
  {
    "cartMenus": [
      {
        "id": 1,
        "name": "김치찌개",
        "price": 8000,
        "quantity": 10,
        "isSoldOut": false,
        "description": "매콤한 김치찌개",
        "links": [
          { "rel": "self", "href": "/api/v1/shops/1/cartMenus/1" }
        ]
      },
      {
        "id": 2,
        "name": "된장찌개",
        "price": 7000,
        "quantity": 0,
        "isSoldOut": true,
        "description": "구수한 된장찌개",
        "links": [
          { "rel": "self", "href": "/api/v1/shops/1/cartMenus/2" }
        ]
      }
    ],
    "total": 12,
    "limit": 50,
    "offset": 0
  }
  ```
- **용도**: 상점 상세 페이지에서 메뉴 목록 표시

### 3.2 메뉴 추가 API (상점 관리자용)
- **엔드포인트**: `POST /api/v1/shops/{shopId}/cartMenus`
- **요청 데이터**:
  ```json
  {
    "name": "김치찌개",
    "price": 8000,
    "quantity": 10,
    "description": "매콤한 김치찌개"
  }
  ```
- **응답 상태 코드**: `201 Created`
- **응답 헤더**: `Location: /api/v1/shops/1/cartMenus/1`
- **응답 데이터**:
  ```json
  {
    "id": 1,
    "name": "김치찌개",
    "price": 8000,
    "quantity": 10,
    "isSoldOut": false,
    "description": "매콤한 김치찌개",
    "links": [
      { "rel": "self", "href": "/api/v1/shops/1/cartMenus/1" },
      { "rel": "shop", "href": "/api/v1/shops/1" }
    ]
  }
  ```
- **용도**: 상점 관리자가 메뉴 추가

### 3.3 메뉴 정보 수정 API (상점 관리자용)
- **엔드포인트**: `PUT /api/v1/shops/{shopId}/cartMenus/{menuId}`
- **요청 데이터**:
  ```json
  {
    "name": "김치찌개",
    "price": 9000,
    "quantity": 20,
    "description": "매콤하고 시원한 김치찌개"
  }
  ```
- **응답 상태 코드**: `200 OK`
- **응답 데이터**:
  ```json
  {
    "id": 1,
    "name": "김치찌개",
    "price": 9000,
    "quantity": 20,
    "isSoldOut": false,
    "description": "매콤하고 시원한 김치찌개",
    "links": [
      { "rel": "self", "href": "/api/v1/shops/1/cartMenus/1" },
      { "rel": "shop", "href": "/api/v1/shops/1" }
    ]
  }
  ```
- **용도**: 상점 관리자가 메뉴 정보 수정

## 4. 장바구니(Cart) 관련 API

### 4.1 장바구니 조회 API
- **엔드포인트**: `GET /api/v1/users/{userId}/cart`
- **응답 상태 코드**: `200 OK`
- **응답 데이터**:
  ```json
  {
    "id": 1,
    "userId": 1,
    "shopId": 1,
    "shopName": "맛있는 한식당",
    "cartMenus": [
      {
        "id": 1,
        "name": "김치찌개",
        "price": 9000,
        "quantity": 2,
        "links": [
          { "rel": "menu", "href": "/api/v1/shops/1/cartMenus/1" }
        ]
      },
      {
        "id": 3,
        "name": "공기밥",
        "price": 1000,
        "quantity": 2,
        "links": [
          { "rel": "menu", "href": "/api/v1/shops/1/cartMenus/3" }
        ]
      }
    ],
    "totalPrice": 20000,
    "links": [
      { "rel": "self", "href": "/api/v1/users/1/cart" },
      { "rel": "shop", "href": "/api/v1/shops/1" },
      { "rel": "checkout", "href": "/api/v1/orders" }
    ]
  }
  ```
- **용도**: 사용자의 장바구니 페이지에서 담은 상품 목록 표시

### 4.2 장바구니 메뉴 추가 API
- **엔드포인트**: `POST /api/v1/users/{userId}/cart/items`
- **요청 데이터**:
  ```json
  {
    "shopId": 1,
    "menuId": 1,
    "quantity": 1
  }
  ```
- **응답 상태 코드**: `201 Created`
- **응답 데이터**:
  ```json
  {
    "id": 1,
    "userId": 1,
    "shopId": 1,
    "cartMenus": [
      {
        "id": 1,
        "name": "김치찌개",
        "price": 9000,
        "quantity": 1,
        "links": [
          { "rel": "menu", "href": "/api/v1/shops/1/cartMenus/1" }
        ]
      }
    ],
    "totalPrice": 9000,
    "links": [
      { "rel": "self", "href": "/api/v1/users/1/cart" },
      { "rel": "shop", "href": "/api/v1/shops/1" }
    ]
  }
  ```
- **오류 응답(400)**:
  ```json
  {
    "error": {
      "code": "InvalidCartOperation",
      "message": "Cannot add items from different shops to the same cart."
    }
  }
  ```
- **용도**: 상품 상세 페이지에서 장바구니에 추가 버튼 클릭 시

### 4.3 장바구니 메뉴 수량 변경 API
- **엔드포인트**: `PATCH /api/v1/users/{userId}/cart/items/{menuId}`
- **요청 데이터**:
  ```json
  {
    "quantity": 3
  }
  ```
- **응답 상태 코드**: `200 OK`
- **응답 데이터**:
  ```json
  {
    "id": 1,
    "userId": 1,
    "shopId": 1,
    "cartMenus": [
      {
        "id": 1,
        "name": "김치찌개",
        "price": 9000,
        "quantity": 3,
        "links": [
          { "rel": "menu", "href": "/api/v1/shops/1/cartMenus/1" }
        ]
      }
    ],
    "totalPrice": 27000,
    "links": [
      { "rel": "self", "href": "/api/v1/users/1/cart" }
    ]
  }
  ```
- **용도**: 장바구니 페이지에서 메뉴 수량 변경 시

### 4.4 장바구니 메뉴 삭제 API
- **엔드포인트**: `DELETE /api/v1/users/{userId}/cart/items/{menuId}`
- **응답 상태 코드**: `204 No Content`
- **응답 데이터**: 없음
- **용도**: 장바구니 페이지에서 메뉴 삭제 시

## 5. 주문(Order) 관련 API

### 5.1 주문 생성 API
- **엔드포인트**: `POST /api/v1/orders`
- **요청 데이터**:
  ```json
  {
    "userId": 1,
    "shopId": 1,
    "items": [
      {
        "menuId": 1,
        "quantity": 2
      },
      {
        "menuId": 3,
        "quantity": 2
      }
    ],
    "deliveryAddress": {
      "street": "서울시 강남구 테헤란로 123",
      "detail": "456호",
      "postalCode": "06123"
    }
  }
  ```
- **응답 상태 코드**: `201 Created`
- **응답 헤더**: `Location: /api/v1/orders/1`
- **응답 데이터**:
  ```json
  {
    "orderId": 1,
    "ordererId": 1,
    "shopId": 1,
    "shopName": "맛있는 한식당",
    "orderDate": "2025-03-04T09:30:45Z",
    "items": [
      {
        "menuId": 1,
        "name": "김치찌개",
        "price": 9000,
        "quantity": 2
      },
      {
        "menuId": 3,
        "name": "공기밥",
        "price": 1000,
        "quantity": 2
      }
    ],
    "totalPrice": 20000,
    "deliveryId": 1,
    "deliveryStatus": "PENDING",
    "links": [
      { "rel": "self", "href": "/api/v1/orders/1" },
      { "rel": "shop", "href": "/api/v1/shops/1" },
      { "rel": "delivery", "href": "/api/v1/deliveries/1" }
    ]
  }
  ```
- **용도**: 장바구니에서 주문하기 버튼 클릭 시 주문 생성

### 5.2 사용자별 주문 목록 조회 API
- **엔드포인트**: `GET /api/v1/users/{userId}/orders`
- **쿼리 파라미터**:
  - `status` (선택적): PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED
  - `startDate` (선택적): ISO 8601 형식 (예: 2025-01-01T00:00:00Z)
  - `endDate` (선택적): ISO 8601 형식
  - `limit` (선택적, 기본값: 20): 페이지당 결과 수
  - `offset` (선택적, 기본값: 0): 페이지네이션 오프셋
- **응답 상태 코드**: `200 OK`
- **응답 데이터**:
  ```json
  {
    "orders": [
      {
        "orderId": 1,
        "shopId": 1,
        "shopName": "맛있는 한식당",
        "orderDate": "2025-03-01T12:30:45Z",
        "totalPrice": 20000,
        "deliveryStatus": "COMPLETED",
        "links": [
          { "rel": "self", "href": "/api/v1/orders/1" },
          { "rel": "shop", "href": "/api/v1/shops/1" }
        ]
      },
      {
        "orderId": 2,
        "shopId": 2,
        "shopName": "신선한 초밥집",
        "orderDate": "2025-03-03T18:15:30Z",
        "totalPrice": 35000,
        "deliveryStatus": "IN_PROGRESS",
        "links": [
          { "rel": "self", "href": "/api/v1/orders/2" },
          { "rel": "shop", "href": "/api/v1/shops/2" }
        ]
      }
    ],
    "total": 8,
    "limit": 20,
    "offset": 0
  }
  ```
- **용도**: 사용자의 주문 내역 페이지

### 5.3 주문 상세 조회 API
- **엔드포인트**: `GET /api/v1/orders/{orderId}`
- **응답 상태 코드**: `200 OK`
- **응답 데이터**:
  ```json
  {
    "orderId": 1,
    "ordererId": 1,
    "shopId": 1,
    "shopName": "맛있는 한식당",
    "orderDate": "2025-03-01T12:30:45Z",
    "items": [
      {
        "menuId": 1,
        "name": "김치찌개",
        "price": 9000,
        "quantity": 2
      },
      {
        "menuId": 3,
        "name": "공기밥",
        "price": 1000,
        "quantity": 2
      }
    ],
    "totalPrice": 20000,
    "deliveryId": 1,
    "deliveryStatus": "COMPLETED",
    "deliveryAddress": {
      "street": "서울시 강남구 테헤란로 123",
      "detail": "456호",
      "postalCode": "06123"
    },
    "links": [
      { "rel": "self", "href": "/api/v1/orders/1" },
      { "rel": "shop", "href": "/api/v1/shops/1" },
      { "rel": "delivery", "href": "/api/v1/deliveries/1" },
      { "rel": "review", "href": "/api/v1/shops/1/reviews" }
    ]
  }
  ```
- **용도**: 주문 상세 페이지

## 6. 배송(Delivery) 관련 API

### 6.1 배송 상태 조회 API
- **엔드포인트**: `GET /api/v1/deliveries/{deliveryId}`
- **응답 상태 코드**: `200 OK`
- **응답 데이터**:
  ```json
  {
    "deliveryId": 1,
    "orderId": 1,
    "status": "IN_PROGRESS",
    "estimatedDeliveryTime": "2025-03-04T18:30:00Z",
    "trackingHistory": [
      {
        "status": "ACCEPTED",
        "timestamp": "2025-03-04T10:15:30Z",
        "description": "주문이 상점에 접수되었습니다."
      },
      {
        "status": "PREPARING",
        "timestamp": "2025-03-04T10:25:45Z",
        "description": "상점에서 주문을 준비 중입니다."
      },
      {
        "status": "IN_PROGRESS",
        "timestamp": "2025-03-04T11:00:15Z",
        "description": "배달이 시작되었습니다."
      }
    ],
    "links": [
      { "rel": "self", "href": "/api/v1/deliveries/1" },
      { "rel": "order", "href": "/api/v1/orders/1" }
    ]
  }
  ```
- **용도**: 주문 상세 페이지에서 배송 상태 표시

### 6.2 배송 상태 변경 API (상점 관리자용)
- **엔드포인트**: `PATCH /api/v1/deliveries/{deliveryId}`
- **요청 데이터**:
  ```json
  {
    "status": "IN_PROGRESS",
    "description": "배달이 시작되었습니다."
  }
  ```
- **응답 상태 코드**: `200 OK`
- **응답 데이터**:
  ```json
  {
    "deliveryId": 1,
    "orderId": 1,
    "status": "IN_PROGRESS",
    "estimatedDeliveryTime": "2025-03-04T18:30:00Z",
    "trackingHistory": [
      {
        "status": "ACCEPTED",
        "timestamp": "2025-03-04T10:15:30Z",
        "description": "주문이 상점에 접수되었습니다."
      },
      {
        "status": "PREPARING",
        "timestamp": "2025-03-04T10:25:45Z",
        "description": "상점에서 주문을 준비 중입니다."
      },
      {
        "status": "IN_PROGRESS",
        "timestamp": "2025-03-04T11:00:15Z",
        "description": "배달이 시작되었습니다."
      }
    ],
    "links": [
      { "rel": "self", "href": "/api/v1/deliveries/1" },
      { "rel": "order", "href": "/api/v1/orders/1" }
    ]
  }
  ```
- **용도**: 상점 관리자가 배송 상태 변경

## 7. 리뷰(Review) 관련 API

### 7.1 상점별 리뷰 목록 조회 API
- **엔드포인트**: `GET /api/v1/shops/{shopId}/reviews`
- **쿼리 파라미터**:
  - `minRating` (선택적): 최소 평점 (1-5)
  - `maxRating` (선택적): 최대 평점 (1-5)
  - `limit` (선택적, 기본값: 20): 페이지당 결과 수
  - `offset` (선택적, 기본값: 0): 페이지네이션 오프셋
  - `sortBy` (선택적, 기본값: "createdAt"): "createdAt", "rating" 중 선택
  - `order` (선택적, 기본값: "desc"): "asc", "desc" 중 선택
- **응답 상태 코드**: `200 OK`
- **응답 데이터**:
  ```json
  {
    "reviews": [
      {
        "id": 1,
        "title": "맛있게 먹었습니다",
        "writerId": 1,
        "writerName": "홍길동",
        "shopId": 1,
        "detail": "김치찌개가 정말 맛있었습니다. 또 방문하고 싶네요!",
        "rating": 4.5,
        "createdAt": "2025-03-02T14:30:45Z",
        "links": [
          { "rel": "self", "href": "/api/v1/shops/1/reviews/1" },
          { "rel": "shop", "href": "/api/v1/shops/1" },
          { "rel": "writer", "href": "/api/v1/users/1" }
        ]
      },
      {
        "id": 2,
        "title": "배달이 빨라요",
        "writerId": 2,
        "writerName": "김철수",
        "shopId": 1,
        "detail": "음식도 맛있고 배달도 빨라요.",
        "rating": 5.0,
        "createdAt": "2025-03-03T11:15:30Z",
        "links": [
          { "rel": "self", "href": "/api/v1/shops/1/reviews/2" },
          { "rel": "shop", "href": "/api/v1/shops/1" },
          { "rel": "writer", "href": "/api/v1/users/2" }
        ]
      }
    ],
    "averageRating": 4.75,
    "total": 32,
    "limit": 20,
    "offset": 0,
    "links": [
      { "rel": "self", "href": "/api/v1/shops/1/reviews?limit=20&offset=0" },
      { "rel": "next", "href": "/api/v1/shops/1/reviews?limit=20&offset=20" }
    ]
  }
  ```
- **용도**: 상점 상세 페이지에서 리뷰 목록 표시

### 7.2 리뷰 작성 API
- **엔드포인트**: `POST /api/v1/shops/{shopId}/reviews`
- **요청 데이터**:
  ```json
  {
    "title": "맛있게 먹었습니다",
    "writerId": 1,
    "detail": "김치찌개가 정말 맛있었습니다. 또 방문하고 싶네요!",
    "rating": 4.5,
    "orderId": 1
  }
  ```
- **응답 상태 코드**: `201 Created`
- **응답 헤더**: `Location: /api/v1/shops/1/reviews/1`
- **응답 데이터**:
  ```json
  {
    "id": 1,
    "title": "맛있게 먹었습니다",
    "writerId": 1,
    "shopId": 1,
    "detail": "김치찌개가 정말 맛있었습니다. 또 방문하고 싶네요!",
    "rating": 4.5,
    "createdAt": "2025-03-04T09:30:45Z",
    "links": [
      { "rel": "self", "href": "/api/v1/shops/1/reviews/1" },
      { "rel": "shop", "href": "/api/v1/shops/1" },
      { "rel": "order", "href": "/api/v1/orders/1" }
    ]
  }
  ```
- **오류 응