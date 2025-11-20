import { inject } from '@angular/core';
import { provideApollo } from 'apollo-angular';
import { HttpLink } from 'apollo-angular/http';
import { ApolloLink, InMemoryCache, split } from '@apollo/client/core';
import { setContext } from '@apollo/client/link/context';
import { HttpHeaders } from '@angular/common/http';
import { environment } from '../environments/environment';
import { TokenStorageAdapter } from './core/services/token-storage.adapter';
import { GraphQLWsLink } from '@apollo/client/link/subscriptions';
import { createClient } from 'graphql-ws';
import { getMainDefinition } from '@apollo/client/utilities';

export const graphqlProvider = provideApollo(() => {
  const httpLink = inject(HttpLink);
  const tokenStorage = inject(TokenStorageAdapter);

  const http = httpLink.create({
    uri: environment.graphqlEndpoint
  });

  const auth = setContext((_, { headers }) => {
    // Guard against SSR: localStorage is only available in the browser
    if (typeof window === 'undefined') {
      return {};
    }

    const token = tokenStorage.getToken();
    if (!token) {
      return {};
    }

    let authHeaders = headers instanceof HttpHeaders ? headers : new HttpHeaders(headers ?? {});
    authHeaders = authHeaders.set('Authorization', `Bearer ${token}`);

    return {
      headers: authHeaders
    };
  });

  // Create WebSocket link for subscriptions (only in browser context)
  let wsLink: GraphQLWsLink | null = null;
  if (typeof window !== 'undefined') {
    // Use separate /graphql-ws endpoint for WebSocket subscriptions
    const wsEndpoint = environment.graphqlEndpoint.replace('https', 'wss').replace('/graphql', '/graphql-ws');
    wsLink = new GraphQLWsLink(createClient({
      url: wsEndpoint,
      connectionParams: () => {
        const token = tokenStorage.getToken();
        return token ? { Authorization: `Bearer ${token}` } : {};
      },
    }));
  }

  // Split link: use WebSocket for subscriptions if available, HTTP otherwise
  const link = wsLink ? split(
    ({ query }) => {
      const definition = getMainDefinition(query);
      return definition.kind === 'OperationDefinition' && definition.operation === 'subscription';
    },
    wsLink,
    auth.concat(http)
  ) : auth.concat(http);

  return {
    link,
    cache: new InMemoryCache(),
    defaultOptions: {
      watchQuery: {
        fetchPolicy: 'cache-and-network',
        errorPolicy: 'all'
      },
      query: {
        fetchPolicy: 'cache-first',
        errorPolicy: 'all'
      },
      mutate: {
        errorPolicy: 'all'
      }
    }
  };
});
