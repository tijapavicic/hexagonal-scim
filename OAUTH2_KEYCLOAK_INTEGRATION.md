# OAuth2 Keycloak Integration Guide

## Overview

This guide explains how to populate the `keycloak_id` field during OAuth2 user login/registration.

## Current State

✅ **Database:** `keycloak_id` column added (V19 migration)  
✅ **Model:** User model includes `keycloakId` field  
✅ **Repository:** `findByKeycloakId()` method implemented  
✅ **API:** `GET /api/v1/users/by-keycloak-id/{keycloakId}` endpoint added  
⚠️ **OAuth2 Flow:** Not yet implemented (manual setup required)

## How OAuth2 Integration Works

### 1. User Authentication Flow

```
┌─────────┐          ┌──────────┐          ┌────────────┐          ┌──────────┐
│ Browser │          │   App    │          │  Keycloak  │          │ Database │
└────┬────┘          └────┬─────┘          └─────┬──────┘          └────┬─────┘
     │                    │                      │                      │
     │  Login Request     │                      │                      │
     ├───────────────────>│                      │                      │
     │                    │  Redirect to KC      │                      │
     │<───────────────────┤                      │                      │
     │                    │                      │                      │
     │  User enters creds │                      │                      │
     ├───────────────────────────────────────────>│                      │
     │                    │                      │                      │
     │  JWT Token         │                      │                      │
     │<───────────────────────────────────────────┤                      │
     │                    │                      │                      │
     │  API request + JWT │                      │                      │
     ├───────────────────>│                      │                      │
     │                    │  Verify JWT          │                      │
     │                    ├─────────────────────>│                      │
     │                    │                      │                      │
     │                    │  Extract sub claim   │                      │
     │                    │  (Keycloak UUID)     │                      │
     │                    │                      │                      │
     │                    │  Find/create user    │                      │
     │                    │  with keycloak_id    │                      │
     │                    ├──────────────────────────────────────────────>│
     │                    │                      │                      │
     │  Response          │                      │                      │
     │<───────────────────┤                      │                      │
     │                    │                      │                      │
```

### 2. JWT Token Structure

Keycloak issues JWT tokens with the following structure:

```json
{
  "sub": "a59ba86d-5c3c-42f3-b15c-a54e623fbb64",  // Keycloak user UUID
  "email": "user@example.com",
  "preferred_username": "user@example.com",
  "name": "John Doe",
  "given_name": "John",
  "family_name": "Doe",
  "email_verified": true
}
```

The `sub` (subject) claim contains the Keycloak user UUID that should be mapped to `keycloak_id` in the database.

## Implementation Steps

### Step 1: Add OAuth2 Configuration

Create `OAuth2UserService` to handle user creation/update on login:

```java
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    
    private final UserRepositoryPort userRepository;
    
    public CustomOAuth2UserService(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }
    
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        
        // Extract Keycloak user ID from JWT
        String keycloakId = oAuth2User.getAttribute("sub");
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        
        // Find or create user
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseGet(() -> {
                    // Create new user with Keycloak ID
                    User newUser = User.createBuyer(null, keycloakId, email, name);
                    return userRepository.save(newUser);
                });
        
        // If keycloak_id is null (existing user before migration), update it
        if (user.keycloakId() == null) {
            User updated = new User(
                user.id(),
                keycloakId,  // Set Keycloak ID
                user.email(),
                user.displayName(),
                user.isSeller(),
                user.sellerDisplayName(),
                user.sellerBio(),
                user.sellerRating(),
                user.sellerReviewCount(),
                user.sellerVerifiedAt(),
                user.sellerJoinedAt()
            );
            user = userRepository.update(updated);
        }
        
        return new CustomOAuth2User(user, oAuth2User.getAttributes());
    }
}
```

### Step 2: Configure Security

Update `SecurityConfig` to use the custom OAuth2 user service:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    private final CustomOAuth2UserService oAuth2UserService;
    
    public SecurityConfig(CustomOAuth2UserService oAuth2UserService) {
        this.oAuth2UserService = oAuth2UserService;
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(oAuth2UserService)
                )
            );
        return http.build();
    }
    
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
        
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return converter;
    }
}
```

### Step 3: Add Custom OAuth2User

Create a custom OAuth2User implementation that wraps the internal User model:

```java
public class CustomOAuth2User implements OAuth2User {
    
    private final User user;
    private final Map<String, Object> attributes;
    
    public CustomOAuth2User(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes;
    }
    
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (user.sellerEnabled()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SELLER"));
        }
        return authorities;
    }
    
    @Override
    public String getName() {
        return user.email();
    }
    
    public User getUser() {
        return user;
    }
    
    public Long getUserId() {
        return user.id();
    }
    
    public String getKeycloakId() {
        return user.keycloakId();
    }
}
```

### Step 4: Update Application Properties

Configure Keycloak connection in `application.yml`:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: ${KEYCLOAK_CLIENT_ID:hexagonal-scim}
            client-secret: ${KEYCLOAK_CLIENT_SECRET}
            scope: openid,profile,email
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
        provider:
          keycloak:
            issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8081/realms/hexagonal}
            user-name-attribute: preferred_username
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8081/realms/hexagonal}
```

## Testing the Integration

### Manual Test

1. **Start the application:**
   ```bash
   cd hex-application
   mvn spring-boot:run
   ```

2. **Create a test user in Keycloak** with UUID `a59ba86d-5c3c-42f3-b15c-a54e623fbb64`

3. **Login via OAuth2** and verify user is created with keycloak_id

4. **Test the endpoint:**
   ```bash
   curl -X GET "http://localhost:8080/api/v1/users/by-keycloak-id/a59ba86d-5c3c-42f3-b15c-a54e623fbb64"
   ```

   **Expected Response:**
   ```json
   {
     "id": 1,
     "email": "user@example.com",
     "displayName": "John Doe"
   }
   ```

### Existing Users (Migration)

For existing users created before V19 migration, their `keycloak_id` will be `NULL`. The OAuth2UserService handles this:

1. User logs in with Keycloak
2. System finds user by email (if exists)
3. If `keycloak_id` is NULL, it's populated from JWT `sub` claim
4. User record is updated with Keycloak ID

## API Usage

### Option 1: Numeric ID (Internal)
```bash
GET /api/v1/users/1
```

### Option 2: Keycloak UUID (External OAuth2 Identity)
```bash
GET /api/v1/users/by-keycloak-id/a59ba86d-5c3c-42f3-b15c-a54e623fbb64
```

### Option 3: Email (Alternative Lookup)
```bash
GET /api/v1/users?email=user@example.com
```

## Database Schema

```sql
-- From V19 migration
ALTER TABLE users ADD COLUMN keycloak_id VARCHAR(255);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_keycloak_id ON users(keycloak_id);
```

## Security Considerations

1. **Unique Constraint:** `keycloak_id` has unique index to prevent duplicate mappings
2. **Null Values:** keycloak_id can be NULL for users created before OAuth2 integration
3. **Immutable:** Once set, keycloak_id should never change (it's the external identity)
4. **Access Control:** Only authenticated users can lookup by Keycloak ID

## Next Steps

- [ ] Implement `CustomOAuth2UserService` class
- [ ] Add OAuth2 security configuration
- [ ] Configure Keycloak connection
- [ ] Test login flow with Keycloak
- [ ] Populate keycloak_id for existing users on first login
- [ ] Add integration tests for OAuth2 flow

## References

- **Migration:** `V19__add_keycloak_id_to_users.sql`
- **Model:** `User.java` (includes keycloakId field)
- **Repository:** `UserRepositoryAdapter.java` (implements findByKeycloakId)
- **Endpoint:** `UserControllerAdapter.java` (GET /api/v1/users/by-keycloak-id/{keycloakId})
- **Port:** `GetUserByKeycloakIdPort.java`
- **Spring Security OAuth2:** https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html
- **Keycloak:** https://www.keycloak.org/documentation

