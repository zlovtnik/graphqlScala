# GraphQL Operations

This directory contains all GraphQL operations (queries and mutations) used by the authentication service.

## Structure

- **login.ts** - Login mutation for user authentication
- **register.ts** - Register mutation for creating new user accounts
- **refreshToken.ts** - Mutation (`REFRESH_TOKEN_MUTATION`) that refreshes authentication tokens
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

## Authoring Operations

Edit the `.ts` files that contain `gql` template literals in `src/app/core/graphql/**/*.ts`. These files are the source of truth for the application's queries and mutations and are what Angular imports at runtime.

### Documentation `.graphql` Files

The `.graphql` files in this directory are retained for documentation and future graphql-codegen adoption. They mirror the TypeScript operations but are not consumed directly by the client today. When code generation is enabled, update `codegen.yml` to include the `.graphql` files and note that generated output will land in `src/app/core/graphql/generated.ts`.

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
