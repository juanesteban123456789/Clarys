// Declaraciones minimas para verificar tipos de las funciones con tsc local.
declare const Deno: {
    env: { get(name: string): string | undefined };
    serve(handler: (request: Request) => Response | Promise<Response>): void;
    test(name: string, test: () => void | Promise<void>): void;
};
