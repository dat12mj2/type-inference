import sys
from pyope.ope import OPE, ValueRange
cipher = OPE(b'key goes here' * 2, in_range=ValueRange(-2**31, 2**31-1), out_range=ValueRange(-2**53, 2**53-1))
input = sys.argv[1]
print cipher.encrypt(int(input))
