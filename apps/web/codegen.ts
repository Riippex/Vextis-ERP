import type { CodegenConfig } from '@graphql-codegen/cli';

const config: CodegenConfig = {
  schema: '../../contracts/graphql/public-api.graphqls',
  documents: ['src/**/*.graphql'],
  generates: {
    'src/app/api/generated/graphql.ts': {
      plugins: ['typescript-operations', 'typescript-apollo-angular'],
    },
  },
  ignoreNoDocuments: false,
};

export default config;
