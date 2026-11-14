// What a hook needs from the argument Payload calls it with.
//
// Payload types an operation hook's argument as one union over every operation, and the union still names `read`, which it
// deprecates in favor of `find` and `findByID`. Reading `operation` off that union therefore reads a deprecated
// member, whatever operation the hook is after. These read it, and the operation's data, through plain shapes.

/**
 * The name of the operation a hook was called for.
 * @param hookArgument the hook's argument.
 * @param hookArgument.operation the name Payload gave the operation.
 * @returns the operation's name, such as `login` or `unlock`.
 */
export const operationOf = (hookArgument: { readonly operation: string }): string => hookArgument.operation

/**
 * A text field of the data an operation was given, read without assuming which operation's arguments these are.
 * @param operationArguments the operation's arguments, which hold the data a caller sent, if any.
 * @param field the field's name, such as `email`.
 * @returns the field's text, or undefined when the data holds no text under that name.
 */
export const textFieldOf = (operationArguments: object, field: string): string | undefined => {
  const data: unknown = 'data' in operationArguments ? operationArguments.data : undefined
  const value: unknown = typeof data === 'object' && data !== null ? Reflect.get(data, field) : undefined
  return typeof value === 'string' ? value : undefined
}

/**
 * The error an `afterError` hook was called for, as it was thrown: an error that arrives through GraphQL is
 * wrapped, and the original is its `originalError`.
 * @param error the error the hook received.
 * @returns the error as it was thrown.
 */
export const originalErrorOf = (error: Error): unknown => ('originalError' in error ? error.originalError : error)
