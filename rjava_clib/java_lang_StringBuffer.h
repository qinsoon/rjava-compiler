#ifndef JAVA_LANG_STRINGBUFFER_H
#define JAVA_LANG_STRINGBUFFER_H

#include "java_lang_Object.h"
#include "java_lang_String.h"
#include "rjava_crt.h"

typedef struct java_lang_StringBuffer{
char* internal;
int curr_buffer_size;
} java_lang_StringBuffer;

#define JAVA_LANG_STRINGBUFFER_INIT_SIZE 1024

inline void java_lang_StringBuffer_rjinit_java_lang_String(java_lang_StringBuffer* this_parameter, java_lang_String* str);
inline void java_lang_StringBuffer_rjinit(java_lang_StringBuffer* this_parameter);
java_lang_StringBuffer* java_lang_StringBuffer_append_java_lang_Object(java_lang_StringBuffer* this_parameter, java_lang_Object* obj);
inline java_lang_StringBuffer* java_lang_StringBuffer_append_int32_t(java_lang_StringBuffer* this_parameter, int32_t i);
inline java_lang_StringBuffer* java_lang_StringBuffer_append_int64_t(java_lang_StringBuffer* this_parameter, int64_t i);
inline java_lang_String* java_lang_StringBuffer_toString(java_lang_StringBuffer* this_parameter);

#endif
