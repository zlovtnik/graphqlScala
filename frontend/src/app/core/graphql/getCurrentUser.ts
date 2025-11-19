import { gql } from 'apollo-angular';

export const GET_CURRENT_USER_QUERY = gql`
  query GetCurrentUser {
    currentUser: getCurrentUser {
      id
      username
      email
    }
  }
`;
