import { inject } from '@angular/core';
import { provideApollo } from 'apollo-angular';
import { HttpLink } from 'apollo-angular/http';
import { ApolloLink, InMemoryCache } from '@apollo/client/core';
import { setContext } from '@apollo/client/link/context';
import { HttpHeaders } from '@angular/common/http';
import { environment } from '../environments/environment';

export const graphqlProvider = provideApollo(() => {
  const httpLink = inject(HttpLink);

  const http = httpLink.create({
    uri: environment.graphqlEndpoint
  });

  const auth = setContext((_, { headers }) => {
    // Guard against SSR: localStorage is only available in the browser
    if (typeof window === 'undefined') {
      return {};
    }

    const token = localStorage.getItem('auth_token');
    if (!token) {
      return {};
    }

    let authHeaders = headers instanceof HttpHeaders ? headers : new HttpHeaders(headers ?? {});
    authHeaders = authHeaders.set('Authorization', `Bearer ${token}`);

    return {
      headers: authHeaders
    };
  });

  return {
    link: ApolloLink.from([auth, http]),
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
