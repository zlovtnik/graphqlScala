import { gql } from 'apollo-angular';

export const REFRESH_TOKEN_MUTATION = gql`
  mutation RefreshToken {
    refreshToken {
      token
      user {
        id
        username
        email
      }
    }
  }
`;
