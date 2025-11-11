# GraphQL Operations

This directory contains all GraphQL operations (queries and mutations) used by the authentication service.

## Structure

- **login.ts** - Login mutation for user authentication
- **register.ts** - Register mutation for creating new user accounts
- **getCurrentUser.ts** - Query to fetch the current authenticated user
- **index.ts** - Barrel export for all operations

## Usage

Import operations from the index file:

```typescript
import {
  LOGIN_MUTATION,
  REGISTER_MUTATION,
  GET_CURRENT_USER_QUERY
} from '../graphql';
```

## GraphQL Operation Files

### login.graphql

Mutation for user login with username and password credentials.

- Input: `username: String!`, `password: String!`
- Output: `token`, `user { id, username, email }`

### register.graphql

Mutation for user registration with account details.

- Input: `username: String!`, `email: String!`, `password: String!`
- Output: `token`, `user { id, username, email }`

### getCurrentUser.graphql

Query to fetch the current authenticated user information.

- Input: None (uses authentication context)
- Output: `currentUser { id, username, email }`

## Code Generation (Future)

To configure GraphQL Code Generator (graphql-codegen) for this directory:

1. Create a `codegen.yml` in the project root with:

   ```yaml
   schema: 'YOUR_GRAPHQL_ENDPOINT'
   documents: 'src/app/core/graphql/**/*.graphql'
   generates:
     src/app/core/graphql/generated.ts:
       plugins:
         - typescript
         - typescript-operations
         - typescript-apollo-angular
   ```

2. Run code generation: `npm run codegen`

This will generate TypeScript types and Angular Apollo service types from the operations.

## Notes

- Currently using TypeScript files (`.ts`) with `gql` template literals
- `.graphql` files are kept for documentation and future code generation setup
- All operations are properly typed for null safety and error handling
- Import paths use barrel exports for clean module organization
