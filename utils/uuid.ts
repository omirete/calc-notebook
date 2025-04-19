import uuid from "react-native-uuid";

export const createUUID = (): string => uuid.v4().toString();
