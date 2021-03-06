package java.lang.reflect;
import checkers.inference.reim.quals.*;

import java.lang.annotation.Annotation;

public final class Field extends AccessibleObject implements Member {
    @ReadonlyThis public Class<?> getDeclaringClass()  {
        throw new RuntimeException("skeleton method");
    }

    @ReadonlyThis public String getName()  {
        throw new RuntimeException("skeleton method");
    }

    @ReadonlyThis public int getModifiers()  {
        throw new RuntimeException("skeleton method");
    }

    @ReadonlyThis public boolean isEnumConstant()  {
        throw new RuntimeException("skeleton method");
    }

    @ReadonlyThis public boolean isSynthetic()  {
        throw new RuntimeException("skeleton method");
    }

    @ReadonlyThis public Class<?> getType()  {
        throw new RuntimeException("skeleton method");
    }

    public Type getGenericType() {
        throw new RuntimeException("skeleton method");
    }

    @ReadonlyThis public boolean equals(@Readonly Object obj)  {
        throw new RuntimeException("skeleton method");
    }

    public int hashCode() {
        throw new RuntimeException("skeleton method");
    }

    public String toString() {
        throw new RuntimeException("skeleton method");
    }

    public String toGenericString() {
        throw new RuntimeException("skeleton method");
    }

    public Object get(Object obj)
        throws IllegalArgumentException, IllegalAccessException
    {
        throw new RuntimeException("skeleton method");
    }

    public boolean getBoolean(Object obj)
        throws IllegalArgumentException, IllegalAccessException
    {
        throw new RuntimeException("skeleton method");
    }

    public byte getByte(Object obj)
        throws IllegalArgumentException, IllegalAccessException
    {
        throw new RuntimeException("skeleton method");
    }

    public char getChar(Object obj)
        throws IllegalArgumentException, IllegalAccessException
    {
        throw new RuntimeException("skeleton method");
    }

    public short getShort(Object obj)
        throws IllegalArgumentException, IllegalAccessException
    {
        throw new RuntimeException("skeleton method");
    }

    public int getInt(Object obj)
        throws IllegalArgumentException, IllegalAccessException
    {
        throw new RuntimeException("skeleton method");
    }

    public long getLong(Object obj)
        throws IllegalArgumentException, IllegalAccessException
    {
        throw new RuntimeException("skeleton method");
    }

    public float getFloat(Object obj)
        throws IllegalArgumentException, IllegalAccessException
    {
        throw new RuntimeException("skeleton method");
    }

    public double getDouble(Object obj)
        throws IllegalArgumentException, IllegalAccessException
    {
        throw new RuntimeException("skeleton method");
    }

    public void set(Object obj, Object value)
        throws IllegalArgumentException, IllegalAccessException
    {
        throw new RuntimeException("skeleton method");
    }

    public void setBoolean(Object obj, boolean z)
        throws IllegalArgumentException, IllegalAccessException
    {
        throw new RuntimeException("skeleton method");
    }

    public void setByte(Object obj, byte b)
        throws IllegalArgumentException, IllegalAccessException
    {
        throw new RuntimeException("skeleton method");
    }

    public void setChar(Object obj, char c)
        throws IllegalArgumentException, IllegalAccessException
    {
        throw new RuntimeException("skeleton method");
    }

    public void setShort(Object obj, short s)
        throws IllegalArgumentException, IllegalAccessException
    {
        throw new RuntimeException("skeleton method");
    }

    public void setInt(Object obj, int i)
        throws IllegalArgumentException, IllegalAccessException
    {
        throw new RuntimeException("skeleton method");
    }

    public void setLong(Object obj, long l)
        throws IllegalArgumentException, IllegalAccessException
    {
        throw new RuntimeException("skeleton method");
    }

    public void setFloat(Object obj, float f)
        throws IllegalArgumentException, IllegalAccessException
    {
        throw new RuntimeException("skeleton method");
    }

    public void setDouble(Object obj, double d)
        throws IllegalArgumentException, IllegalAccessException
    {
        throw new RuntimeException("skeleton method");
    }

    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        throw new RuntimeException("skeleton method");
    }

    public Annotation[] getDeclaredAnnotations()  {
        throw new RuntimeException("skeleton method");
    }
}
