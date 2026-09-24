# Product Requirements Document (PRD) for Ecommerce Website

## Functional Requirements

### 1. User Management
- **Registration:** Allow new users to create an account using their email or social media profiles.
- **Login:** Users should be able to securely log in using their credentials.
- **Profile Management:** Users should have the ability to view and modify their profile details.
- **Password Reset:** Users must have the option to reset their password through a secure link.

### 2. Product Catalog
- **Browsing:** Users should be able to browse products by different categories.
- **Product Details:** Detailed product pages with product images, descriptions, specifications, and other relevant information.
- **Search:** Users must be able to search for products using keywords.

### 3. Cart & Checkout
- **Add to Cart:** Users should be able to add products to their cart.
- **Cart Review:** View selected items in the cart with price, quantity, and total details.
- **Checkout:** Seamless process to finalize the purchase, including specifying delivery address and payment method.

### 4. Order Management
- **Order Confirmation:** After making a purchase, users should receive a confirmation with order details.
- **Order History:** Users should be able to view their past orders.
- **Order Tracking:** Provide users with a way to track their order's delivery status.

### 5. Payment
- **Multiple Payment Options:** Support for credit/debit cards, online banking, and other popular payment methods.
- **Secure Transactions:** Ensure user trust by facilitating secure payment transactions.
- **Payment Receipt:** Provide users with a receipt after a successful payment.

### 6. Authentication
- **Secure Authentication:** Ensure that user data remains private and secure during login and throughout their session.
- **Session Management:** Users should remain logged in for a specified duration or until they decide to log out.

---

## High-Level Design (HLD) for Ecommerce Website

### Architecture Components
- Load Balancers (LB)
- API Gateway
- Microservices
- Databases (Relational and NoSQL)
- Message Broker (Kafka)
- Caching (Redis)
- Search and Analytics (Elasticsearch)

---

### 1. Load Balancers (LB)
- **Function:** Distribute incoming user requests across multiple server instances to balance load and ensure high availability.
- **Tool:** Amazon Elastic Load Balancing (ELB).

### 2. API Gateway
- **Function:** Entry point for clients. Routes requests to the right microservices, handles rate limiting, and manages authentication.
- **Tool:** Kong.

### 3. Microservices Architecture
#### 3.1 User Management Service
- Handles user registration, login, profile management, and password reset.
- Uses MySQL as the primary database for structured user data.
- Uses Kafka to communicate relevant user activities to other services.

#### 3.2 Product Catalog Service
- Manages product listings, details, categorization.
- Uses MySQL.
- Incorporates Elasticsearch for fast product searches.

#### 3.3 Cart Service
- Manages user's shopping cart.
- Uses MongoDB for flexibility in cart structures.
- Uses Redis for fast, in-memory data access.

#### 3.4 Order Management Service
- Handles order processing, history, and tracking.
- Uses MySQL.
- Communicates with Payment Service and User Management Service through Kafka.

#### 3.5 Payment Service
- Manages payment gateways and transaction logs.
- Uses MySQL.
- Produces Kafka messages to notify Order Management Service after payment confirmation.

#### 3.6 Notification Service
- Manages email and potentially other notifications (e.g., SMS).
- Consumes Kafka messages for events requiring user notifications.
- Integrates with Amazon SES for email delivery.

---

### 4. Databases
- **MySQL:** For structured data.
- **MongoDB:** For flexible, unstructured data.

### 5. Kafka
- Central message broker allowing asynchronous communication between microservices.

### 6. Caching with Redis
- Primarily used by Cart Service for faster response times.

### 7. Elasticsearch
- Used by Product Catalog for fast and relevant product searches.

---

## Typical Flow with Kafka & Elasticsearch Integration

1. **User logs in and searches for a product**
   - Request → LB → API Gateway → Product Catalog Service → Elasticsearch.

2. **User adds a product to the cart**
   - Cart Service produces a message to Kafka.

3. **User checks out**
   - Order Management Service triggered.
   - Payment Service consumes Kafka message to process payment.
